#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

caddy_command="$({
    HEIDI_PUBLIC_HOST=https://dev.example
    HEIDI_HTTPS_PORT=9443
    HEIDI_PUBLIC_BASE_URL=https://dev.example:9443
    just --justfile "$root_dir/justfile" --no-dotenv --dry-run dev-caddy 2>&1
})"
grep -Fq 'up --menu=false caddy' <<<"$caddy_command"

startup_message="$({
    HEIDI_PUBLIC_HOST=https://dev.example
    HEIDI_HTTPS_PORT=9443
    HEIDI_CA_PORT=80
    HEIDI_PUBLIC_BASE_URL=https://dev.example:9443
    just --justfile "$root_dir/justfile" --no-dotenv heidi-banner
})"
grep -Fq '██╗  ██╗███████╗██╗██████╗ ██╗' <<<"$startup_message"
grep -Fq 'HEIDI IS READY' <<<"$startup_message"
grep -Fq 'HTTPS       https://dev.example:9443' <<<"$startup_message"
grep -Fq 'HTTP        http://dev.example' <<<"$startup_message"
grep -Fq 'Install CA  http://dev.example/ca.crt' <<<"$startup_message"
grep -Fq 'Stop        Ctrl+C' <<<"$startup_message"
grep -Fq 'dev-ready skip' "$root_dir/justfile"
grep -Fq '${HEIDI_CA_PORT:-80}:80' "$root_dir/compose.yaml"
grep -Fq '@ca path /ca.crt' "$root_dir/local/Caddyfile"
grep -Fq 'auto_https disable_redirects' "$root_dir/local/Caddyfile"
[[ "$(grep -Fc 'import heidi_routes' "$root_dir/local/Caddyfile")" == 2 ]]
