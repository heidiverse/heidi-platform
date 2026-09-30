<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Extensibility

Heidi is intended to be extended without changing the OID4VCI and OID4VP core
flows. Keep reusable protocol capability in OSS. Keep deployment-specific
product policy, persistence, identity integrations, and private service
connectors in an extension package or a separate service.

This page describes the general OSS extension seams. Provider-specific product
names, routes, and implementation details stay outside the core distribution.

## Extension surfaces

| Area | OSS seam | Typical use |
| --- | --- | --- |
| Platform backend | Spring beans in the assembled Platform API application | New management routes, services, policies, or integrations. |
| Process orchestration | `ProcessActionExtension` | Add a deployment-specific process action selected by a stable string. |
| Public routes | `PublicPathExtension` | Add an extension-owned route that performs its own authentication. |
| Platform database | `PlatformApiExtensionMigration` | Add extension tables without sharing the core Flyway history. |
| Issuer database | `IssuerExtensionMigration` | Add issuer-extension tables without sharing the core Flyway history. |
| Issuer authorization | `IssuerAuthorizationExtension` | Add an authorization-code flow backed by a deployment IdP/AS. |
| Cockpit | `registerExtension` and `CockpitExtension` | Add navigation, dashboard segments, slots, messages, feature groups, or an identity provider. |
| Signing | `SigningKeyProvider` and optional capability interfaces | Put an HSM, KMS, or qualified provider behind the [Signing Protocol](signing-protocol.md); see the [Frostline example](examples/custom-hsm-signing-service.md). |

The extension interfaces live in the independent API modules where possible.
An extension should compile against those APIs and be assembled with a
compatible OSS revision.

## Backend extension layout

A backend extension can be a Maven module with its own package, tests, and
configuration:

```text
my-platform-extension/
├── pom.xml
└── src/
    ├── main/java/.../MyExtensionConfiguration.java
    ├── main/java/.../MyController.java
    ├── main/resources/db/migration/extensions/my-extension/postgresql/
    │   └── V1__create_extension_tables.sql
    └── test/java/.../MyExtensionTest.java
```

The assembled application must make the extension beans visible through
component scanning or Spring Boot auto-configuration. Loading a JAR alone is
not a contract: verify the assembled application, its routes, and its
database migrations together.

### Process actions

`ProcessActionExtension` is the boundary for actions that are not part of the
OSS action set. Use a stable action string and keep extension-specific input in
`InitializeProcessRequest.extensionData` or the generic process payload. The
OSS coordinator can then preserve its normal process-token and lifecycle
handling while the extension owns its policy and external calls.

```java
@Component
final class ExampleActionExtension implements ProcessActionExtension {
    @Override
    public boolean supports(String action) {
        return "example.action".equals(action);
    }

    // Validate extensionData, call the private integration, and return the
    // normal Heidi process response. This is illustrative pseudocode.
}
```

Do not use action names to smuggle provider-specific state into core tables.
Persist extension state in extension-owned tables and retain tenant scoping
with the repository's `tenant` convention.

### Migrations

Implement `PlatformApiExtensionMigration` or `IssuerExtensionMigration` and
return a stable lowercase extension ID plus a classpath migration location.
The runtime executes extension migrations after core migrations and gives each
extension its own Flyway history table, such as
`flyway_schema_history_ext_<extension-id>`.

Keep extension migrations in the extension package. Do not edit or reuse the
core migration history. If an extension migration is renamed or removed, run
`just clean` before rebuilding, for the same reason as core migrations.

Routes that are intentionally public must be registered through
`PublicPathExtension` and must authenticate themselves with their own
capability, API-key, or protocol mechanism. Never make a management route
public just to make an extension work.

## Cockpit extensions

The Cockpit has a registry-based extension boundary. A distribution can add a
normal frontend package that calls `registerExtension` during startup:

```ts
registerExtension({
  id: "example-extension",
  navGroups: [/* links */],
  dashboardSegments: [/* tenant-aware cards */],
  slots: { /* components for named core slots */ },
  messages: { /* locale loaders */ },
  identityProvider: /* optional deployment identity adapter */,
});
```

Use runtime `/config.json` for deployment URLs and other non-secret settings.
Keep secrets and access tokens out of the bundle. An identity-aware deployment
may replace the default platform identity provider, but the replacement must
still return the Cockpit user and tenant model expected by the core UI.

Frontend extension packages should be versioned with the OSS revision they
target. A package that imports internal components or assumes an unrelated
route layout is coupled to that revision and needs an explicit compatibility
policy.

### Frontend package branding

A frontend extension package may declare optional app branding in its
`package.json`:

```json
{
  "heidi": {
    "web": {
      "branding": {
        "appName": "Example",
        "icon": "/assets/example-icon.svg",
        "themeColor": "#336699"
      }
    }
  }
}
```

`appName` is required when branding is declared. `icon` is a root-relative URL
for an asset in the web app's public directory, and `themeColor` is optional.
The Cockpit HTML shell uses Vite placeholders for these values; extension
packages supply metadata and assets without carrying a separate HTML file. The
same metadata updates the web app manifest. Builds without extension branding
retain the default Heidi identity.

## Extension rules

- Preserve the existing OID4VCI/OID4VP flow state, process tokens, metadata,
  QR codes, deferred issuance, refresh, and active presentation requests.
- Keep tenant-owned data keyed by `tenant_id`; use `organisation` only for
  human-facing labels and URLs.
- Prefer a generic interface, string action, or JSON extension-data seam over a
  core dependency on one deployment's provider.
- Keep provider credentials, signing keys, IdP client secrets, and private
  URLs outside source control and container images.
- Add contract, integration, and migration tests to the extension. Test the
  assembled application, not only the extension JAR in isolation.
- Document every new flag, route, database, public endpoint, and operational
  dependency in the extension's own documentation.

## Example

The [HSM-backed signing-service example](examples/custom-hsm-signing-service.md)
shows how to implement a fictional provider behind the existing signing
protocol without changing the Platform, Issuer, or Verifier.
