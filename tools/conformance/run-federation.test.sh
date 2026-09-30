#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script="$root_dir/tools/conformance/run-federation.sh"
properties="$root_dir/heidi-platform-api/heidi-platform-api-ws/src/main/resources/application-local.properties"

# Conformance must probe the identity created by local bootstrap.
script_identity="$(sed -n 's/^federation_identity="${HEIDI_CONFORMANCE_IDENTITY:-\([^}]*\)}"$/\1/p' "$script")"
bootstrap_identity="$(sed -n 's/^heidi\.platform\.local\.issuer-slug=//p' "$properties")"

if [[ "$script_identity" != "$bootstrap_identity" ]]; then
    echo "Conformance identity '$script_identity' differs from local bootstrap '$bootstrap_identity'" >&2
    exit 1
fi

# CI installs this artifact from the signer-interface job. Do not invoke Gradle again.
if ! CONFORMANCE_SIGNER_INTERFACE_INSTALLED=true MAVEN=true GRADLE=false \
    just --justfile "$root_dir/justfile" build-signing >/dev/null; then
    echo "Conformance rebuilt an already-installed signer interface" >&2
    exit 1
fi

if ! CONFORMANCE_SIGNER_INTERFACE_INSTALLED=true MAVEN=true GRADLE=false \
    just --justfile "$root_dir/justfile" conformance-build >/dev/null; then
    echo "Federation conformance rebuilt an already-installed signer interface" >&2
    exit 1
fi
