# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set dotenv-load := true
set dotenv-filename := ".env.local"
set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

compose := env_var_or_default("COMPOSE", "docker compose --env-file .env.local")
maven := env_var_or_default("MAVEN", "mvn")
platform_maven := env_var_or_default("MAVEN_PLATFORM", maven)
issuer_maven := env_var_or_default("MAVEN_ISSUER", maven)
verifier_maven := env_var_or_default("MAVEN_VERIFIER", maven)
pnpm := env_var_or_default("PNPM", "pnpm")
gradle := env_var_or_default("GRADLE", "./gradlew")
# Optional compatibility override, e.g. `KAPUNSDK_VERSION=1.0.0-RC4 just dev`.
kapunsdk_version := env_var_or_default("KAPUNSDK_VERSION", "")
maven_kapunsdk_args := if kapunsdk_version == "" { "" } else { "-Dkapunsdk.version=" + kapunsdk_version }
signer_interface_dir := env_var_or_default("SIGNER_INTERFACE_DIR", "heidi-signing-crypto")
# Shared modules are installed before the service builds.
shared_exclusions := "!:heidi-shared-core,!:heidi-signing-api,!:heidi-signing-client"
spring_boot_maven_plugin := "org.springframework.boot:spring-boot-maven-plugin:run"
spring_boot_native_access_args := "-Dspring-boot.run.jvmArguments=--enable-native-access=ALL-UNNAMED"

# Ports the local services listen on. A second checkout that wants to run its
# own stack alongside this one overrides them in its .env.local; the `local`
# Spring profile imports the same file, so the services and every URL they hand
# each other move together.
export HEIDI_PLATFORM_PORT := env_var_or_default("HEIDI_PLATFORM_PORT", "8080")
export HEIDI_ISSUER_PORT := env_var_or_default("HEIDI_ISSUER_PORT", "8082")
export HEIDI_VERIFIER_PORT := env_var_or_default("HEIDI_VERIFIER_PORT", "8083")
export HEIDI_PLATFORM_MANAGEMENT_PORT := env_var_or_default("HEIDI_PLATFORM_MANAGEMENT_PORT", "8081")
export HEIDI_ISSUER_MANAGEMENT_PORT := env_var_or_default("HEIDI_ISSUER_MANAGEMENT_PORT", "8084")
export HEIDI_VERIFIER_MANAGEMENT_PORT := env_var_or_default("HEIDI_VERIFIER_MANAGEMENT_PORT", "8085")
export HEIDI_SIGNING_PORT := env_var_or_default("HEIDI_SIGNING_PORT", "8086")
export HEIDI_WEB_PORT := env_var_or_default("HEIDI_WEB_PORT", "5173")
export HEIDI_HTTPS_PORT := env_var_or_default("HEIDI_HTTPS_PORT", "8443")
export HEIDI_CA_PORT := env_var_or_default("HEIDI_CA_PORT", "80")
export HEIDI_PUBLIC_HOST := env_var_or_default("HEIDI_PUBLIC_HOST", "https://localhost")
export HEIDI_PUBLIC_BASE_URL := env_var_or_default("HEIDI_PUBLIC_BASE_URL", HEIDI_PUBLIC_HOST + ":" + HEIDI_HTTPS_PORT)

export HEIDI_PLATFORM_PUBLIC_BASE_URL := env_var_or_default("HEIDI_PLATFORM_PUBLIC_BASE_URL", HEIDI_PUBLIC_BASE_URL)
export HEIDI_ISSUER_PUBLIC_BASE_URL := env_var_or_default("HEIDI_ISSUER_PUBLIC_BASE_URL", HEIDI_PUBLIC_BASE_URL + "/issuance")
export HEIDI_VERIFIER_PUBLIC_BASE_URL := env_var_or_default("HEIDI_VERIFIER_PUBLIC_BASE_URL", HEIDI_PUBLIC_BASE_URL + "/presentation")
export HEIDI_PLATFORM_WEB_BASE_URL := env_var_or_default("HEIDI_PLATFORM_WEB_BASE_URL", env_var_or_default("HEIDI_WEB_BASE_URL", HEIDI_PUBLIC_BASE_URL))
export HEIDI_VERIFIER_WEB_BASE_URL := env_var_or_default("HEIDI_VERIFIER_WEB_BASE_URL", env_var_or_default("HEIDI_WEB_BASE_URL", HEIDI_PUBLIC_BASE_URL))
export HEIDI_PLATFORM_BASE_URL := env_var_or_default("HEIDI_PLATFORM_BASE_URL", "http://127.0.0.1:" + HEIDI_PLATFORM_PORT)
export HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL := env_var_or_default("HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL", HEIDI_PLATFORM_BASE_URL)
export HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL := env_var_or_default("HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL", env_var_or_default("HEIDI_ISSUER_BASE_URL", "http://127.0.0.1:" + HEIDI_ISSUER_PORT))
export HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL := env_var_or_default("HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL", env_var_or_default("HEIDI_VERIFIER_INTERNAL_BASE_URL", "http://127.0.0.1:" + HEIDI_VERIFIER_PORT))
export HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL := env_var_or_default("HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL", env_var_or_default("HEIDI_ENTITY_BASE_URL", HEIDI_PLATFORM_BASE_URL))
# Legacy names remain exported so older local scripts continue to reach the same services.
export HEIDI_ISSUER_BASE_URL := env_var_or_default("HEIDI_ISSUER_BASE_URL", HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL)
export HEIDI_VERIFIER_INTERNAL_BASE_URL := env_var_or_default("HEIDI_VERIFIER_INTERNAL_BASE_URL", HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL)
export HEIDI_ENTITY_BASE_URL := env_var_or_default("HEIDI_ENTITY_BASE_URL", HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL)
export HEIDI_SIGNING_BASE_URL := env_var_or_default("HEIDI_SIGNING_BASE_URL", "http://127.0.0.1:" + HEIDI_SIGNING_PORT)

# Show available commands
default:
    @just --list

# Install frontend dependencies and create the root local environment file
setup: local-env
    cd heidi-web && {{pnpm}} install

# Remove generated backend and frontend build output
clean:
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} clean
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} clean
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} clean
    {{maven}} -f heidi-signing-service/pom.xml clean
    rm -rf heidi-web/dist

# `just dev without verifier` (or `without issuer verifier`) leaves services
# out, e.g. to run them yourself in IntelliJ.
# Build and start all local services in parallel.
dev *names:
    #!/usr/bin/env bash
    set -Eeuo pipefail
    names=({{names}})
    if [[ "${names[0]:-}" != "without" && -n "${names[*]:-}" ]]; then
        echo "usage: just dev [without <api|issuer|verifier|signing|web>...]" >&2
        exit 1
    fi
    just build
    just run {{names}}

# Run `just build` after a fresh checkout or backend/build-source change.
# Run using existing build output.
run *names:
    #!/usr/bin/env bash
    set -Eeuo pipefail
    names=({{names}})
    skip=""
    if [[ "${names[0]:-}" == "without" ]]; then
        skip="${names[*]:1}"
    elif [[ -n "${names[*]:-}" ]]; then
        echo "usage: just run [without <api|issuer|verifier|signing|web>...]" >&2
        exit 1
    fi
    just local-env
    just web-deps
    just dev-db
    just dev-services "$skip"

# Keeping installs out of the parallel run phase means a Ctrl-C (or any other
# kill) while services are running can never interrupt a `mvn install` mid-write
# and corrupt ~/.m2 — only long-running, always-safe-to-kill `spring-boot:run`
# processes are running in parallel at that point.
# Build artifacts needed by `just run`, sequentially.
build: local-env build-signing-adapters
    #!/usr/bin/env bash
    set -Eeuo pipefail
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} -pl heidi-platform-api-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-issuer-ws -am install -DskipTests
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-verifier-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{maven}} -f heidi-signing-service/pom.xml install -DskipTests

# Backward-compatible alias for `run`.
dev-fast *names: (run names)

# Run all test targets sequentially.
test: test-local-dev test-conformance-config test-api test-issuer test-verifier test-signing test-signing-adapters

# Verify local development process and URL reporting.
test-local-dev:
    tools/local-dev.test.sh

# Verify conformance uses the identity created by local bootstrap.
test-conformance-config:
    tools/conformance/run-federation.test.sh

[private]
dev-ready skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    readonly attempts=240
    readonly delay=0.25
    skip="{{skip}}"

    # Report readiness only after every selected service accepts requests.
    wait_url() {
        local name="$1"
        local url="$2"
        for ((attempt = 1; attempt <= attempts; attempt++)); do
            curl --fail --silent --output /dev/null "$url" && return
            sleep "$delay"
        done

        echo "$name did not become ready: $url" >&2
        return 1
    }

    wait_url "HTTPS proxy" "http://127.0.0.1:${HEIDI_CA_PORT}/ca.crt"
    [[ " $skip " == *" api "* ]] || wait_url "Platform API" "http://127.0.0.1:${HEIDI_PLATFORM_PORT}/health"
    [[ " $skip " == *" issuer "* ]] || wait_url "Issuer" "http://127.0.0.1:${HEIDI_ISSUER_PORT}/health"
    [[ " $skip " == *" verifier "* ]] || wait_url "Verifier" "http://127.0.0.1:${HEIDI_VERIFIER_PORT}/health"
    [[ " $skip " == *" signing "* ]] || wait_url "Signing" "http://127.0.0.1:${HEIDI_SIGNING_PORT}/actuator/health"
    [[ " $skip " == *" web "* ]] || wait_url "Cockpit" "http://127.0.0.1:${HEIDI_WEB_PORT}/"
    just heidi-banner "$skip"

[private]
heidi-banner skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ca_host="${HEIDI_PUBLIC_HOST#https://}"
    http_address="${ca_host}"
    [[ "${HEIDI_CA_PORT}" == 80 ]] || http_address="${ca_host}:${HEIDI_CA_PORT}"
    wordmark=(
        "██╗  ██╗███████╗██╗██████╗ ██╗"
        "██║  ██║██╔════╝██║██╔══██╗██║"
        "███████║█████╗  ██║██║  ██║██║"
        "██╔══██║██╔══╝  ██║██║  ██║██║"
        "██║  ██║███████╗██║██████╔╝██║"
        "╚═╝  ╚═╝╚══════╝╚═╝╚═════╝ ╚═╝"
    )
    details=(
        "HTTPS       ${HEIDI_PUBLIC_BASE_URL}"
        "HTTP        http://${http_address}"
        "Install CA  http://${http_address}/ca.crt"
        "Stop        Ctrl+C"
    )
    [[ -z "{{skip}}" ]] || details+=("Skipped     {{skip}}")

    width=56
    for line in "${details[@]}"; do
        (( ${#line} > width )) && width=${#line}
    done
    printf -v separator '%*s' "$width" ''
    separator="${separator// /─}"

    red=""
    green=""
    gray=""
    reset=""
    if [[ -t 1 ]]; then
        red=$'\033[91m'
        green=$'\033[92m'
        gray=$'\033[90m'
        reset=$'\033[0m'
    fi

    printf '\n'
    for line in "${wordmark[@]}"; do
        printf '  %s%s%s\n' "$red" "$line" "$reset"
    done
    printf '  %s%s%s\n' "$gray" "$separator" "$reset"
    printf '  %sHEIDI IS READY%s\n\n' "$green" "$reset"
    for line in "${details[@]}"; do
        printf '  %s\n' "$line"
    done
    printf '\n'

[parallel]
[private]
dev-services skip="": (dev-caddy) (run-api skip) (run-issuer skip) (run-verifier skip) (run-signing skip) (run-web skip) (dev-ready skip)

[private]
dev-caddy: local-env
    {{compose}} up --menu=false caddy

# Start Caddy for a standalone service debug run.
start-caddy: local-env caddy-message
    {{compose}} up -d caddy

[private]
caddy-message:
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ca_host="${HEIDI_PUBLIC_HOST#https://}"
    ca_address="${ca_host}"
    [[ "${HEIDI_CA_PORT}" == 80 ]] || ca_address="${ca_host}:${HEIDI_CA_PORT}"
    echo "Caddy HTTPS: ${HEIDI_PUBLIC_BASE_URL} (port ${HEIDI_HTTPS_PORT})"
    echo "Install CA: http://${ca_address}/ca.crt"

# Stop the detached Caddy used by a standalone debug run.
stop-caddy: local-env
    {{compose}} stop caddy

[private]
run-api skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    [[ " {{skip}} " == *" api "* ]] && exit 0
    {{platform_maven}} -f heidi-platform-api/heidi-platform-api-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

[private]
run-issuer skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    [[ " {{skip}} " == *" issuer "* ]] && exit 0
    {{issuer_maven}} -f heidi-issuer-backend/heidi-issuer-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

[private]
run-verifier skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    [[ " {{skip}} " == *" verifier "* ]] && exit 0
    {{verifier_maven}} -f heidi-verifier-backend/heidi-verifier-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

[private]
run-signing skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    [[ " {{skip}} " == *" signing "* ]] && exit 0
    {{maven}} -f heidi-signing-service/software/service/pom.xml {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local

[private]
run-web skip="":
    #!/usr/bin/env bash
    set -Eeuo pipefail
    [[ " {{skip}} " == *" web "* ]] && exit 0
    cd heidi-web && {{pnpm}} run dev:local

# Alias for `dev`
dev-all *names: (dev names)

# Print the CA path and the trust command for a browser or phone.
trust-local-ca: start-caddy
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ca=.local/caddy/data/caddy/pki/authorities/local/root.crt
    for _ in {1..20}; do
        [[ -s "${ca}" ]] && break
        sleep 0.25
    done
    [[ -s "${ca}" ]] || { echo "Caddy local CA was not generated" >&2; exit 1; }
    echo "CA certificate: ${ca}"
    echo "macOS: security add-trusted-cert -d -r trustRoot -k ~/Library/Keychains/login.keychain-db ${ca}"

# Start the local Postgres container hosting all service databases
dev-db: local-env
    {{compose}} up -d --wait platform-postgres
    {{compose}} exec -T platform-postgres /docker-entrypoint-initdb.d/10-databases.sh

# Stop the local Postgres container
stop-db: local-env
    {{compose}} stop platform-postgres

# Delete and recreate the local Postgres volume
reset-db: local-env
    {{compose}} down -v
    {{compose}} up -d --wait platform-postgres
    {{compose}} exec -T platform-postgres /docker-entrypoint-initdb.d/10-databases.sh

# Run the combined backend API locally
dev-api: local-env start-caddy build-signing-adapters
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} -pl heidi-platform-api-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{platform_maven}} -f heidi-platform-api/heidi-platform-api-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

# Publish the Ed25519ph signer module to Maven Local
prepare-signer-interface:
    cd {{signer_interface_dir}} && {{gradle}} publishToMavenLocal

# Build and install the shared backend module once, so parallel dev tasks don't race on it
dev-shared: prepare-signer-interface
    {{maven}} -f heidi-shared-backend/pom.xml install -DskipTests

# Run the issuer backend locally
dev-issuer: local-env start-caddy build-signing-adapters
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-issuer-ws -am install -DskipTests
    {{issuer_maven}} -f heidi-issuer-backend/heidi-issuer-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

# Run the verifier backend locally
dev-verifier: local-env start-caddy build-signing-adapters
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-verifier-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{verifier_maven}} -f heidi-verifier-backend/heidi-verifier-ws/pom.xml {{maven_kapunsdk_args}} {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local,no-security

# Run the reference signing service locally
dev-signing: local-env dev-db prepare-signer-interface
    {{maven}} -f heidi-signing-service/pom.xml install -DskipTests
    {{maven}} -f heidi-signing-service/software/service/pom.xml {{spring_boot_maven_plugin}} {{spring_boot_native_access_args}} -Dspring-boot.run.profiles=local

# Run the cockpit frontend locally
dev-web skip="": local-env start-caddy
    just run-web "{{skip}}"

# Build platform API modules
build-api: build-signing-adapters
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} install

# Build issuer backend modules
build-issuer: build-signing-adapters
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} install

# Build verifier backend modules
build-verifier: build-signing-adapters
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} install

# Build the reference signing service
build-signing:
    #!/usr/bin/env bash
    set -Eeuo pipefail
    if [[ "${CONFORMANCE_SIGNER_INTERFACE_INSTALLED:-false}" != "true" ]]; then
        just prepare-signer-interface
    fi
    {{maven}} -f heidi-signing-service/pom.xml install

# Build provider-backed certificate and JWS adapters
build-signing-adapters: dev-shared

# Build frontend
build-web:
    cd heidi-web && {{pnpm}} run build

# Regenerate the committed integrator and management OpenAPI specifications
openapi: build-signing-adapters
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} -pl heidi-platform-api-ws -am -Dtest=OpenApiSnapshotIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true test

# Run platform API tests
test-api: build-signing-adapters
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} test

# Run issuer backend tests
test-issuer: build-signing-adapters
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} test

# Run verifier backend tests
test-verifier: build-signing-adapters
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} test

# Run signing service tests
test-signing: prepare-signer-interface
    {{maven}} -f heidi-signing-service/pom.xml test

# Run signing adapter tests
test-signing-adapters: prepare-signer-interface dev-shared
    {{maven}} -f heidi-shared-backend/signing-client/pom.xml test

# Run the official OpenID Federation deployed-entity conformance plan
conformance-federation:
    tools/conformance/run-federation.sh

# Run the official OID4VCI and OID4VP conformance plans. The runner starts
# both wallet-facing services and the OpenID Foundation suite containers.
conformance-oid4vc: conformance-build-oid4vc
    tools/conformance/run-oid4vc.sh

# Build only the services exercised by the Federation conformance runner. The
# runner prepares the signer interface first for local runs; CI can install the
# signer artifact produced by the normal signer-interface job.
[private]
conformance-build:
    # CI installs the signer artifact; local runs build it before the shared reactor.
    if [[ "${CONFORMANCE_SIGNER_INTERFACE_INSTALLED:-false}" != "true" ]]; then just prepare-signer-interface; fi
    {{maven}} -f heidi-shared-backend/pom.xml install -DskipTests
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} -pl heidi-platform-api-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-verifier-ws -am -pl '{{shared_exclusions}}' install -DskipTests

# Build every backend used by the OID4VCI and OID4VP conformance runner.
# CI installs the signer-interface artifact; local runs build it on demand.
[private]
conformance-build-oid4vc:
    # The shared reactor provides the signing adapters consumed by all three services.
    if [[ "${CONFORMANCE_SIGNER_INTERFACE_INSTALLED:-false}" != "true" ]]; then just prepare-signer-interface; fi
    {{maven}} -f heidi-shared-backend/pom.xml install -DskipTests
    {{platform_maven}} -f heidi-platform-api/pom.xml {{maven_kapunsdk_args}} -pl heidi-platform-api-ws -am -pl '{{shared_exclusions}}' install -DskipTests
    {{issuer_maven}} -f heidi-issuer-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-issuer-ws -am install -DskipTests
    {{verifier_maven}} -f heidi-verifier-backend/pom.xml {{maven_kapunsdk_args}} -pl heidi-verifier-ws -am -pl '{{shared_exclusions}}' install -DskipTests

# Run frontend linting
lint-web:
    cd heidi-web && {{pnpm}} run lint

# Apply safe frontend lint fixes
lint-web-fix:
    cd heidi-web && {{pnpm}} run lint:write

[private]
local-env:
    #!/usr/bin/env bash
    set -Eeuo pipefail
    if ! test -f .env.local; then cp .env.local.example .env.local; fi
    if [[ "${HEIDI_PUBLIC_HOST}" != https://* ]]; then
        echo "HEIDI_PUBLIC_HOST must use https:// for local development" >&2
        exit 1
    fi
    if grep -q '^HEIDI_SIGNING_ENCRYPTION_MASTER_KEY=' .env.local; then
        grep -Eq '^HEIDI_SIGNING_ENCRYPTION_MASTER_KEY=[0-9a-fA-F]{64}$' .env.local || {
            echo "HEIDI_SIGNING_ENCRYPTION_MASTER_KEY must contain exactly 64 hexadecimal characters" >&2
            exit 1
        }
    else
        signing_key="$(openssl rand -hex 32)"
        printf '\n# Generated for this local checkout; do not commit or delete while the signing database is in use.\nHEIDI_SIGNING_ENCRYPTION_MASTER_KEY=%s\n' "$signing_key" >> .env.local
    fi

[private]
web-deps:
    test -d heidi-web/node_modules || (cd heidi-web && {{pnpm}} install)
