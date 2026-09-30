# API structure

Application APIs are grouped by caller.

| Audience | Prefix | Authentication |
| --- | --- | --- |
| Cockpit and operators | `/management/v1` | Platform JWT and tenant authorization |
| Integrating backends | `/integration/v1` | API key or server credential |
| Browsers and web components | `/interaction/v1` | Narrow interaction capability |
| Platform services | `/internal/platform/v1` | Service authentication |
| Issuer services | `/internal/issuer/v1` | Service authentication |
| Verifier services | `/internal/verifier/v1` | Service authentication |
| Wallet protocols and metadata | Existing advertised paths | Protocol-specific |
| Infrastructure | `/health`, `/healthz`, `/actuator/*` | Deployment-specific |

Management uses `/management/v1/identities`, `/management/v1/credential-schemas`, and
`/management/v1/proof-schemas`. A credential schema is shared by issuance and verification.
It also contains issuer-specific settings used by the current issuance configuration.

DTO, JSON and database identifiers containing `credentialScheme` use the same neutral term.

The recommended deployment uses three hosts:

```text
api.example.com       -> heidi-platform-api
issuer.example.com    -> heidi-issuer-backend
verifier.example.com  -> heidi-verifier-backend
```

Internal clients use Kubernetes service DNS, not those public hosts. Public ingress must
deny `/internal/*`; service authentication remains mandatory. A shared hostname is also
supported when routes are explicit:

```text
/management/*, /integration/*, /interaction/*, /public/*, /federation/* -> platform
issuer discovery and advertised OID4VCI paths                            -> issuer
/v1/wallet/* and verifier federation paths                               -> verifier
/internal/*                                                               -> no public route
```

Issuer root-level discovery paths must be routed explicitly. Advertised protocol
and metadata URLs are public contract values.

Springdoc exposes the complete integrator contract through `/api-docs/integrator`; it combines
backend `/integration/*` and browser `/interaction/*` operations. The Cockpit API viewer shows only
this group and disables browser-side test requests, because API keys and process tokens belong on an
integrating backend. It still provides copyable curl examples.

The generated integrator and management specifications are committed under `docs/openapi/`.
Run `just openapi` after changing controllers or models. CI rejects stale snapshots. The signing
service retains its separate `/v1` protocol contract.
