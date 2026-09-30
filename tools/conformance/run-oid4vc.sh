#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

suite_compose() {
    ${CONFORMANCE_COMPOSE:-docker compose} "$@"
}

heidi_compose() {
    ${heidi_compose_cmd} "$@"
}

just_cmd="${JUST:-just}"
compose_project="${CONFORMANCE_COMPOSE_PROJECT:-heidi-oidf-conformance}"
heidi_compose_project="${CONFORMANCE_HEIDI_COMPOSE_PROJECT:-heidi-oidf-stack}"
heidi_compose_cmd="${CONFORMANCE_HEIDI_COMPOSE:-docker compose --env-file .env.local -p ${heidi_compose_project}}"
database_port="${CONFORMANCE_DB_PORT:-55432}"
suite_port="${CONFORMANCE_SUITE_PORT:-8443}"
suite_url="${CONFORMANCE_SUITE_URL:-https://127.0.0.1:${suite_port}}"
output_dir="${CONFORMANCE_OUTPUT_DIR:-tmp/conformance/oidf}"
start_heidi="${CONFORMANCE_START_HEIDI:-true}"
conformance_host="${HEIDI_CONFORMANCE_HOST:-host.docker.internal}"
platform_port="${HEIDI_PLATFORM_PORT:-8080}"
platform_management_port="${HEIDI_PLATFORM_MANAGEMENT_PORT:-8081}"
issuer_port="${HEIDI_ISSUER_PORT:-8082}"
verifier_port="${HEIDI_VERIFIER_PORT:-8083}"
signing_port="${HEIDI_SIGNING_PORT:-8086}"
schema_manifest="${HEIDI_SCHEMA_SEED_MANIFEST:-$ROOT_DIR/tools/conformance/oidf-schema-seed.json}"
credential_jwks_file="${HEIDI_CONFORMANCE_CREDENTIAL_JWKS_FILE:-$ROOT_DIR/tools/conformance/oidf-credential-jwks.json}"
dev_ca_keystore="${HEIDI_DEV_CA_KEYSTORE:-$ROOT_DIR/heidi-platform-api/heidi-platform-api-ws/.local/dev-ca.p12}"
credential_identifier="${HEIDI_CONFORMANCE_CREDENTIAL_IDENTIFIER:-pid}"
credential_version="${HEIDI_CONFORMANCE_CREDENTIAL_VERSION:-1.0}"
credential_configuration_id="${HEIDI_CONFORMANCE_CREDENTIAL_CONFIGURATION_ID:-${credential_identifier}-${credential_version}-sd-jwt}"

# Heidi OSS currently exposes a public-client pre-authorized-code flow. The
# suite's issuer flow variants require confidential-client authentication.
export CONFORMANCE_OIDF_ISSUER_MODULES="${CONFORMANCE_OIDF_ISSUER_MODULES:-oid4vci-1_0-issuer-metadata-test,oid4vci-1_0-issuer-metadata-test-signed}"

mkdir -p "$output_dir"

api_pid=""
issuer_pid=""
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
    suite_compose -p "$compose_project" -f tools/conformance/compose.yaml logs --no-color >"$output_dir/conformance-suite.log" 2>&1
    suite_compose -p "$compose_project" -f tools/conformance/compose.yaml down --remove-orphans >"$output_dir/conformance-suite-down.log" 2>&1
    kill_tree "$verifier_pid"
    kill_tree "$issuer_pid"
    kill_tree "$api_pid"
    kill_tree "$signing_pid"
    if [[ "$start_heidi" == "true" ]]; then
        heidi_compose down --volumes --remove-orphans >"$output_dir/heidi-compose-down.log" 2>&1
    fi
    if [[ -n "$keystore_dir" ]]; then
        rm -rf "$keystore_dir"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

wait_for_url() {
    local url="$1"
    local label="$2"
    local deadline=$((SECONDS + ${CONFORMANCE_SERVICE_TIMEOUT:-240}))
    until curl --silent --show-error --fail --insecure "$url" >/dev/null 2>&1; do
        if (( SECONDS >= deadline )); then
            echo "Timed out waiting for $label at $url" >&2
            return 1
        fi
        sleep 2
    done
}

if [[ "$start_heidi" == "true" ]]; then
    # Inner just calls validate the local public host before starting dependencies.
    export HEIDI_PUBLIC_HOST="https://${conformance_host}"
    export PLATFORM_POSTGRES_PORT="$database_port"
    export COMPOSE="$heidi_compose_cmd"

    if [[ "${CONFORMANCE_BUILD_SIGNING:-true}" == "true" ]]; then
        "$just_cmd" build-signing
    fi
    "$just_cmd" dev-db
    SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration \
        HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED=false \
        "$just_cmd" run-signing >"$output_dir/heidi-signing.log" 2>&1 &
    signing_pid="$!"
    wait_for_url "http://127.0.0.1:${signing_port}/v1/capabilities" "Heidi signing provider"

    export HEIDI_PLATFORM_PUBLIC_BASE_URL="https://${conformance_host}:${platform_port}"
    export HEIDI_PLATFORM_BASE_URL="https://127.0.0.1:${platform_port}"
    export HEIDI_ISSUER_PUBLIC_BASE_URL="https://${conformance_host}:${issuer_port}"
    export HEIDI_VERIFIER_PUBLIC_BASE_URL="https://${conformance_host}:${verifier_port}"
    export HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL="https://127.0.0.1:${platform_port}"
    export HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL="https://127.0.0.1:${issuer_port}"
    export HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL="https://127.0.0.1:${verifier_port}"
    export HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL="https://127.0.0.1:${platform_port}"
    export OID4VP_VERIFIER_PREDEFINED_JWKS="{'https://${conformance_host}:${suite_port}/test/a/heidi-oid4vp':'file:${credential_jwks_file}'}"

    export HEIDI_SCHEMA_SEED_ENABLED=true
    export HEIDI_SCHEMA_SEED_MANIFEST="$schema_manifest"
    export HEIDI_PLATFORM_LOCAL_TRUST_SYSTEMS="${HEIDI_PLATFORM_LOCAL_TRUST_SYSTEMS:-EUDI}"

    keystore_dir="$(mktemp -d "${TMPDIR:-/tmp}/heidi-oidf-keystore.XXXXXX")"
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
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${keystore_dir}/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
    export SERVER_SSL_ENABLED=true
    export SERVER_SSL_KEY_STORE="file:${keystore_dir}/heidi-conformance.p12"
    export SERVER_SSL_KEY_STORE_PASSWORD=changeit
    export SERVER_SSL_KEY_STORE_TYPE=PKCS12
    export SERVER_SSL_KEY_ALIAS=heidi-conformance

    "$just_cmd" run-api >"$output_dir/heidi-platform.log" 2>&1 &
    api_pid="$!"
    "$just_cmd" run-issuer >"$output_dir/heidi-issuer.log" 2>&1 &
    issuer_pid="$!"
    "$just_cmd" run-verifier >"$output_dir/heidi-verifier.log" 2>&1 &
    verifier_pid="$!"

    wait_for_url "https://127.0.0.1:${platform_management_port}/actuator/health" "Heidi platform API"
    wait_for_url "https://127.0.0.1:${issuer_port}/health" "Heidi issuer"
    wait_for_url "https://127.0.0.1:${verifier_port}/health" "Heidi verifier"

    export HEIDI_CONFORMANCE_CREDENTIAL_CONFIGURATION_ID="$credential_configuration_id"
    export HEIDI_CONFORMANCE_CREDENTIAL_ISSUER_URL="${HEIDI_ISSUER_PUBLIC_BASE_URL}/${HEIDI_CONFORMANCE_IDENTITY:-acme}/c/${credential_identifier}/${credential_version}"
    export HEIDI_CONFORMANCE_VERIFIER_CLIENT_ID="${HEIDI_CONFORMANCE_VERIFIER_CLIENT_ID:-https://${conformance_host}:${verifier_port}}"
    export HEIDI_CONFORMANCE_TRUST_ANCHOR_PEM="$(keytool -exportcert -rfc -alias dev-ca -keystore "$dev_ca_keystore" -storepass heidi-local-dev)"
fi

export CONFORMANCE_SUITE_PORT="$suite_port"
export CONFORMANCE_FIXED_ALIASES="${CONFORMANCE_FIXED_ALIASES:-true}"
suite_compose -p "$compose_project" -f tools/conformance/compose.yaml up -d

python3 tools/conformance/run-oidf.py \
    --suite-url "$suite_url" \
    --platform-url "https://127.0.0.1:${platform_port}" \
    --output-dir "$output_dir" \
    --issuer-slug "${HEIDI_CONFORMANCE_IDENTITY:-acme}" \
    --credential-identifier "$credential_identifier" \
    --credential-version "$credential_version"
