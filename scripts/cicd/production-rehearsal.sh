#!/usr/bin/env bash
# Only a disposable local PostgreSQL service is accepted. Never source a URL
# or credential from the invoking environment for this rehearsal.
set -euo pipefail
port="${REHEARSAL_PORT:-5432}"
[[ "$port" =~ ^[0-9]{1,5}$ ]] && (( port >= 1 && port <= 65535 )) || { echo 'Invalid rehearsal port'; exit 1; }
export FLYWAY_URL="jdbc:postgresql://127.0.0.1:${port}/rehearsal"
export FLYWAY_USER='postgres'
export FLYWAY_PASSWORD='rehearsal-only'
./mvnw -B -Pdb-migrate flyway:info
./mvnw -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate
./mvnw -B -Pdb-migrate flyway:migrate
./mvnw -B -Pdb-migrate '-Dflyway.ignoreMigrationPatterns=' flyway:validate
./mvnw -B -Pdb-migrate flyway:info
