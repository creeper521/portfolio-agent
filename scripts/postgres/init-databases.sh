#!/usr/bin/env bash
set -Eeuo pipefail

required=(
  POSTGRES_USER
  PORTFOLIO_PUBLIC_DATABASE_NAME
  PORTFOLIO_PUBLIC_DATABASE_USERNAME
  PORTFOLIO_PUBLIC_DATABASE_PASSWORD
  PORTFOLIO_GOVERNANCE_DATABASE_NAME
  PORTFOLIO_GOVERNANCE_DATABASE_USERNAME
  PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD
)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "POSTGRES_LOCAL_REQUIRED_ENV_MISSING" >&2
    exit 1
  fi
done

for value in \
  "$POSTGRES_USER" \
  "$PORTFOLIO_PUBLIC_DATABASE_NAME" \
  "$PORTFOLIO_PUBLIC_DATABASE_USERNAME" \
  "$PORTFOLIO_GOVERNANCE_DATABASE_NAME" \
  "$PORTFOLIO_GOVERNANCE_DATABASE_USERNAME"; do
  if [[ ! "$value" =~ ^[a-z_][a-z0-9_]{0,62}$ ]]; then
    echo "POSTGRES_LOCAL_IDENTIFIER_INVALID" >&2
    exit 1
  fi
done

if [[ "$PORTFOLIO_PUBLIC_DATABASE_NAME" == "$PORTFOLIO_GOVERNANCE_DATABASE_NAME" ]] ||
   [[ "$PORTFOLIO_PUBLIC_DATABASE_USERNAME" == "$PORTFOLIO_GOVERNANCE_DATABASE_USERNAME" ]] ||
   [[ "$PORTFOLIO_PUBLIC_DATABASE_USERNAME" == "$POSTGRES_USER" ]] ||
   [[ "$PORTFOLIO_GOVERNANCE_DATABASE_USERNAME" == "$POSTGRES_USER" ]]; then
  echo "POSTGRES_LOCAL_IDENTIFIERS_NOT_DISTINCT" >&2
  exit 1
fi

for password in \
  "$PORTFOLIO_PUBLIC_DATABASE_PASSWORD" \
  "$PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD"; do
  if [[ ! "$password" =~ ^[A-Za-z0-9_@%+=:,./!?~-]{12,}$ ]]; then
    echo "POSTGRES_LOCAL_PASSWORD_UNSAFE" >&2
    exit 1
  fi
done

psql --username "$POSTGRES_USER" --dbname postgres \
  --set=ON_ERROR_STOP=1 \
  --set=public_db="$PORTFOLIO_PUBLIC_DATABASE_NAME" \
  --set=public_user="$PORTFOLIO_PUBLIC_DATABASE_USERNAME" \
  --set=public_password="$PORTFOLIO_PUBLIC_DATABASE_PASSWORD" \
  --set=governance_db="$PORTFOLIO_GOVERNANCE_DATABASE_NAME" \
  --set=governance_user="$PORTFOLIO_GOVERNANCE_DATABASE_USERNAME" \
  --set=governance_password="$PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'public_user', :'public_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'public_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'public_user', :'public_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'public_db', :'public_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'public_db') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'public_db', :'public_user') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'governance_user', :'governance_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'governance_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'governance_user', :'governance_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'governance_db', :'governance_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'governance_db') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'governance_db', :'governance_user') \gexec
SQL

for database in \
  "$PORTFOLIO_PUBLIC_DATABASE_NAME" \
  "$PORTFOLIO_GOVERNANCE_DATABASE_NAME"; do
  psql --username "$POSTGRES_USER" --dbname "$database" \
    --set=ON_ERROR_STOP=1 \
    --command='CREATE EXTENSION IF NOT EXISTS vector' >/dev/null
done

echo "POSTGRES_LOCAL_DATABASES_READY"
