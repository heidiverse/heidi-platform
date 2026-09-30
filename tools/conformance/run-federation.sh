#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

compose() {
    ${COMPOSE:-docker compose} "$@"
}

just_cmd="${JUST:-just}"
compose_project="${CONFORMANCE_COMPOSE_PROJECT:-heidi-conformance}"
suite_port="${CONFORMANCE_SUITE_PORT:-8443}"
suite_url="${CONFORMANCE_SUITE_URL:-https://127.0.0.1:${suite_port}}"
output_dir="${CONFORMANCE_OUTPUT_DIR:-tmp/conformance/federation}"
start_heidi="${CONFORMANCE_START_HEIDI:-true}"
conformance_host="${HEIDI_CONFORMANCE_HOST:-host.docker.internal}"
platform_port="${HEIDI_PLATFORM_PORT:-8080}"
verifier_port="${HEIDI_VERIFIER_PORT:-8083}"
signing_port="${HEIDI_SIGNING_PORT:-8086}"
federation_identity="${HEIDI_CONFORMANCE_IDENTITY:-acme}"

mkdir -p "$output_dir"

api_pid=""
verifier_pid=""
signing_pid=""
keystore_dir=""

kill_tree() {
    local pid="$1"
    [[ -n "$pid" ]] || return 0
    local child
    for child in $(pgrep -P "$pid" 2>/dev/null || true); do
        kill_tree "$child"
    done
    kill "$pid" 2>/dev/null || true
}

cleanup() {
    local exit_code="$?"
    set +e
    compose -p "$compose_project" -f tools/conformance/compose.yaml logs --no-color >"$output_dir/conformance-suite.log" 2>&1
    compose -p "$compose_project" -f tools/conformance/compose.yaml down --remove-orphans >"$output_dir/conformance-suite-down.log" 2>&1
    kill_tree "$verifier_pid"
    kill_tree "$api_pid"
    kill_tree "$signing_pid"
    if [[ -n "$keystore_dir" ]]; then
        rm -rf "$keystore_dir"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

wait_for_url() {
    local url="$1"
    local label="$2"
    local deadline=$((SECONDS + ${CONFORMANCE_SERVICE_TIMEOUT:-180}))
    until curl --silent --show-error --fail --insecure "$url" >/dev/null 2>&1; do
        if (( SECONDS >= deadline )); then
            echo "Timed out waiting for $label at $url" >&2
            return 1
        fi
        sleep 2
    done
}

if [[ "$start_heidi" == "true" ]]; then
    # The platform's local bootstrap provisions an issuer through the signing
    # protocol. Start the reference provider before enabling TLS for the API
    # and verifier; the provider itself is intentionally HTTP in local mode.
    # Scope the Boot 4 JDBC exclusion to the in-memory signing process; the API
    # and verifier need their PostgreSQL datasource and JPA entity manager.
    "$just_cmd" build-signing
    "$just_cmd" dev-db
    HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED=false \
        SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration \
        "$just_cmd" run-signing >"$output_dir/heidi-signing.log" 2>&1 &
    signing_pid="$!"
    wait_for_url "http://127.0.0.1:${signing_port}/v1/capabilities" "Heidi signing provider"

    # Public identifiers use the suite's host name; service-to-service calls stay on loopback.
    export HEIDI_PLATFORM_PUBLIC_BASE_URL="https://${conformance_host}:${platform_port}"
    export HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL="https://127.0.0.1:${platform_port}"
    export HEIDI_VERIFIER_PUBLIC_BASE_URL="https://${conformance_host}:${verifier_port}"

    keystore_dir="$(mktemp -d "${TMPDIR:-/tmp}/heidi-conformance-keystore.XXXXXX")"
    keytool -genkeypair -noprompt \
        -alias heidi-conformance \
        -keyalg EC \
        -groupname secp256r1 \
        -storetype PKCS12 \
        -keystore "$keystore_dir/heidi-conformance.p12" \
        -storepass changeit \
        -keypass changeit \
        -dname "CN=${conformance_host}" \
        -ext "SAN=dns:${conformance_host},dns:localhost,ip:127.0.0.1" \
        -validity 1
    # The verifier asks the platform to sign its entity configuration, so it must trust the
    # self-signed certificate. Start from the JDK's CA certificates so Maven keeps working.
    keytool -exportcert -rfc \
        -alias heidi-conformance \
        -keystore "$keystore_dir/heidi-conformance.p12" \
        -storepass changeit \
        -file "$keystore_dir/heidi-conformance.pem"
    java_home="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.home/ {print $2}')"
    cp "$java_home/lib/security/cacerts" "$keystore_dir/truststore.p12"
    chmod u+w "$keystore_dir/truststore.p12"
    keytool -importcert -noprompt \
        -alias heidi-conformance \
        -file "$keystore_dir/heidi-conformance.pem" \
        -keystore "$keystore_dir/truststore.p12" \
        -storepass changeit
    verifier_java_options="-Djavax.net.ssl.trustStore=${keystore_dir}/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"

    export SERVER_SSL_ENABLED=true
    export SERVER_SSL_KEY_STORE="file:${keystore_dir}/heidi-conformance.p12"
    export SERVER_SSL_KEY_STORE_PASSWORD=changeit
    export SERVER_SSL_KEY_STORE_TYPE=PKCS12
    export SERVER_SSL_KEY_ALIAS=heidi-conformance

    "$just_cmd" conformance-build

    "$just_cmd" run-api >"$output_dir/heidi-platform.log" 2>&1 &
    api_pid="$!"
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} ${verifier_java_options}" \
        "$just_cmd" run-verifier >"$output_dir/heidi-verifier.log" 2>&1 &
    verifier_pid="$!"

    # The platform's local bootstrap makes the local identity its own trust anchor.
    wait_for_url "https://127.0.0.1:${platform_port}/federation/${federation_identity}/.well-known/openid-federation" "Heidi trust anchor"
    wait_for_url "https://127.0.0.1:${verifier_port}/${federation_identity}/.well-known/openid-federation" "Heidi verifier"
fi

export HEIDI_CONFORMANCE_ENTITY_IDENTIFIER="${HEIDI_CONFORMANCE_ENTITY_IDENTIFIER:-https://${conformance_host}:${verifier_port}/${federation_identity}}"
export HEIDI_CONFORMANCE_TRUST_ANCHOR="${HEIDI_CONFORMANCE_TRUST_ANCHOR:-https://${conformance_host}:${platform_port}/federation/${federation_identity}}"

# The anchor's key lives in the signing service and is created at first start, so read the JWKS
# from its entity configuration instead of pinning it.
if [[ -z "${HEIDI_CONFORMANCE_TRUST_ANCHOR_JWKS:-}" ]]; then
    anchor_configuration="$(curl --silent --show-error --fail --insecure \
        "https://127.0.0.1:${platform_port}/federation/${federation_identity}/.well-known/openid-federation")"
    HEIDI_CONFORMANCE_TRUST_ANCHOR_JWKS="$(python3 -c '
import base64, json, sys
payload = sys.argv[1].split(".")[1]
claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
print(json.dumps(claims["jwks"]))
' "$anchor_configuration")"
fi
export HEIDI_CONFORMANCE_TRUST_ANCHOR_JWKS

export CONFORMANCE_SUITE_PORT="$suite_port"
compose -p "$compose_project" -f tools/conformance/compose.yaml up -d
wait_for_url "$suite_url/api/runner/available" "OpenID Foundation conformance suite"

# Leaves have no fetch endpoint, so the fetch error modules only run against the authority.
HEIDI_CONFORMANCE_ENTITY_IDENTIFIER="$HEIDI_CONFORMANCE_ENTITY_IDENTIFIER" python3 tools/conformance/run-plan.py \
    --suite-url "$suite_url" \
    --config-template tools/conformance/federation-plan.json \
    --output-dir "$output_dir/oidf/leaf"
HEIDI_CONFORMANCE_ENTITY_IDENTIFIER="$HEIDI_CONFORMANCE_TRUST_ANCHOR" python3 tools/conformance/run-plan.py \
    --suite-url "$suite_url" \
    --config-template tools/conformance/federation-plan.json \
    --output-dir "$output_dir/oidf/trust-anchor"
