#!/bin/sh
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

set -eu

ensure_database() {
  local database="$1"
  local user="$2"
  local password="$3"

  psql \
    --username "$POSTGRES_USER" \
    --dbname postgres \
    --set=ON_ERROR_STOP=1 \
    --set=database="$database" \
    --set=database_user="$user" \
    --set=database_password="$password" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE NOT EXISTS (
  SELECT FROM pg_catalog.pg_roles WHERE rolname = :'database_user'
)
\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE EXISTS (
  SELECT FROM pg_catalog.pg_roles WHERE rolname = :'database_user'
)
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'database_user')
WHERE NOT EXISTS (
  SELECT FROM pg_catalog.pg_database WHERE datname = :'database'
)
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'database', :'database_user')
WHERE EXISTS (
  SELECT FROM pg_catalog.pg_database WHERE datname = :'database'
)
\gexec
EOSQL
}

ensure_database \
  "$POSTGRES_DB" \
  "$POSTGRES_USER" \
  "$POSTGRES_PASSWORD"
ensure_database \
  "$ISSUER_POSTGRES_DB" \
  "$ISSUER_POSTGRES_USER" \
  "$ISSUER_POSTGRES_PASSWORD"
ensure_database \
  "$VERIFIER_POSTGRES_DB" \
  "$VERIFIER_POSTGRES_USER" \
  "$VERIFIER_POSTGRES_PASSWORD"
ensure_database \
  "$SIGNING_POSTGRES_DB" \
  "$SIGNING_POSTGRES_USER" \
  "$SIGNING_POSTGRES_PASSWORD"
