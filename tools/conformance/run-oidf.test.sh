#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
runner="$root_dir/tools/conformance/run-oid4vc.sh"
python_runner="$root_dir/tools/conformance/run-oidf.py"

test -x "$runner" || { echo "OIDF runner is not executable" >&2; exit 1; }
python3 -c 'compile(open("tools/conformance/run-oidf.py", encoding="utf-8").read(), "tools/conformance/run-oidf.py", "exec")'

# TLS must cover every service-to-service client, not only public endpoints.
grep -Fq 'HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL="https://127.0.0.1:${platform_port}"' "$runner"
grep -Fq 'HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL="https://127.0.0.1:${issuer_port}"' "$runner"
grep -Fq 'HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL="https://127.0.0.1:${verifier_port}"' "$runner"
grep -Fq 'HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL="https://127.0.0.1:${platform_port}"' "$runner"

python3 - "$python_runner" <<'PY'
import importlib.util
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
spec = importlib.util.spec_from_file_location("run_oidf", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
assert module.runner_path("https://host.docker.internal:8443/test/a/heidi") == "/test/a/heidi"
assert module.NoRedirect().redirect_request(None, None, 302, None, {}, None) is None
PY

python3 - "$root_dir" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
issuer = json.loads((root / "tools/conformance/oid4vci-plan.json").read_text())
verifier = json.loads((root / "tools/conformance/oid4vp-plan.json").read_text())
assert "vci" in issuer and "credential_issuer_url" in issuer["vci"]
assert "client" in verifier and "request_object_trust_anchor_pem" in verifier["client"]
assert verifier["browser"][0]["tasks"][0]["commands"][0][-1] == "update-image-placeholder"
credential = json.loads((root / "tools/conformance/oid4vci-seed-credential.json").read_text())
proof = json.loads((root / "tools/conformance/oid4vp-seed-proof.json").read_text())
seed = json.loads((root / "tools/conformance/oidf-schema-seed.json").read_text())
jwks = json.loads((root / "tools/conformance/oidf-credential-jwks.json").read_text())
assert credential["id"] == proof["credentialSchemes"][0]["id"]
assert len(seed["entries"]) == 1
assert seed["entries"][0]["credentialSchemas"] == ["oid4vci-seed-credential.json"]
assert seed["entries"][0]["proofSchemas"] == ["oid4vp-seed-proof.json"]
assert credential["issuerSettings"]["supportedCredentialTypes"] == ["SD_JWT"]
assert proof["verifierClientIdScheme"] == "x509_hash"
assert jwks["keys"][0]["x"] == verifier["credential"]["signing_jwk"]["x"]
assert "d" not in jwks["keys"][0]
PY

echo "OIDF conformance harness configuration is valid"
