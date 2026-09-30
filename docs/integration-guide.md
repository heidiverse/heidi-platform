<!--
  SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
  SPDX-License-Identifier: Apache-2.0
-->

# Integrator guide

Heidi runs issuance and presentation as short-lived processes. The integrating backend owns the
process. A browser or Web Component only receives a restricted client interaction token.

```text
Integrator backend                 Browser                  Heidi
       |                              |                       |
       |-- initialize (API key) ----------------------------->|
       |<------------- processId + processToken --------------|
       |-- start (API key + processToken) -------------------->|
       |<---------- clientInteractionToken --------------------|
       |                              |                       |
       |-- clientInteractionToken --->|                       |
       |                              |-- interact ----------->|
       |                              |<-- wallet hand-off ----|
       |                              |                       |
       |-- result (API key) --------------------------------->|
       |<------------- state + disclosures -------------------|
```

## Tokens and trust boundaries

| Credential | Used by | Purpose |
| --- | --- | --- |
| API key | Integrator backend | Create, start, and read processes. |
| `processToken` | Integrator backend | One-process capability exchanged when starting the process. Never send it to a browser. |
| `clientInteractionToken` | Browser or Web Component | Short-lived access to browser-safe interaction data. |

The API key and `processToken` are backend secrets. The client interaction token is intentionally
safe to pass to the browser, but it remains short-lived and process-specific.

## 1. Initialize from the backend

Call `POST /integration/v1/processes` with the integration API key.

```sh
curl --request POST "$HEIDI_BASE_URL/integration/v1/processes" \
  --header "Authorization: ApiKey $HEIDI_API_KEY" \
  --header "Content-Type: application/json" \
  --data @initialize.json
```

An issuance body identifies a published credential schema and supplies its values:

```json
{
  "action": "pre_auth_issuance",
  "preAuthIssuanceData": {
    "schemaIdentifier": {
      "credentialIdentifier": "employee-card",
      "version": "1.0.0"
    },
    "values": {
      "givenName": "Ada"
    }
  }
}
```

A presentation body identifies a published proof schema:

```json
{
  "action": "presentation",
  "presentationData": {
    "proofSchemeId": "00000000-0000-0000-0000-000000000000",
    "useDcApi": false
  }
}
```

Store the returned `processId` and `processToken` on the backend.

## 2. Start from the backend

```sh
curl --request POST \
  "$HEIDI_BASE_URL/integration/v1/processes/$PROCESS_ID/start" \
  --header "Authorization: ApiKey $HEIDI_API_KEY" \
  --header "Content-Type: application/json" \
  --data "{\"processToken\":\"$PROCESS_TOKEN\"}"
```

Send only the returned `clientInteractionToken` to the browser.

## 3. Handle browser interaction

### Web Components

Install `@heidiverse/heidi-web-components`, initialize it with the Heidi base URL, then render a
component with the client interaction token:

```ts
import { config } from "@heidiverse/heidi-web-components/config";
import "@heidiverse/heidi-web-components";

config.init({ baseUrl: HEIDI_BASE_URL });

const component = document.querySelector("hwc-lightbox");
component.clientInteractionToken = clientInteractionToken;
component.open = true;
```

```html
<hwc-lightbox locale="en"></hwc-lightbox>
```

### Custom frontend

Read browser-safe state with the client interaction token:

```sh
curl "$HEIDI_BASE_URL/interaction/v1/processes/current" \
  --header "Authorization: Bearer $CLIENT_INTERACTION_TOKEN"
```

The response contains the process state, wallet hand-off data, resolved client configuration, and
requested display claims. It never contains the integration API key, process token, or complete
backend result.

## 4. Read the result from the backend

Poll the result endpoint until the returned connection state is terminal:

```sh
curl "$HEIDI_BASE_URL/integration/v1/processes/$PROCESS_ID/result" \
  --header "Authorization: ApiKey $HEIDI_API_KEY"
```

Presentation disclosures are returned here. A raw VP token is included only when the process was
initialized with `includeVpToken: true`.

## References

- [Integrator OpenAPI](openapi/integrator.json) covers backend and browser calls.
- [Management OpenAPI](openapi/management.json) covers Cockpit and administrative automation and
  is not part of the integration flow.

Regenerate both specifications after changing controllers or models:

```sh
just openapi
```

CI regenerates the files and rejects uncommitted differences.
