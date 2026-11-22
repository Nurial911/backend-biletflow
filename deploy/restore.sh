#!/usr/bin/env bash
# Restores a backup made by backup.sh: replaces the WHOLE database, and the posters when the backup has them.
# The pipeline copies this file to ~/app; it never runs it. See deploy/BACKUPS.md.
#
#   bash ~/app/restore.sh backups/nightly/20261122-023000 --yes
#   bash ~/app/restore.sh backups/db-20261121-230400-before-a6d15519a262.sql.gz --yes
#       (a single .sql.gz from before backup folders existed: database only, posters stay as they are)
#
# 1. Backs up the current state to backups/pre-restore/, so the restore itself can be undone.
# 2. Stops the backend, so nothing writes while the database is replaced.
# 3. Drops and recreates the database and loads the dump. An older dump is fine: on start, Flyway brings
#    its schema up to the running version.
# 4. Replaces the posters.
# 5. Starts the backend and waits until it is healthy.
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/app}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

log() { echo "[restore $(date +%H:%M:%S)] $*"; }

SOURCE="${1:?usage: restore.sh <backup folder or .sql.gz file> --yes}"
cd "$APP_DIR"

if [ -d "$SOURCE" ]; then
    DUMP="$SOURCE/db.sql.gz"
    UPLOADS="$SOURCE/uploads.tar.gz"
    [ -f "$UPLOADS" ] || UPLOADS=""
else
    DUMP="$SOURCE"
    UPLOADS=""
fi
case "$SOURCE" in *.partial | *.partial/) log "$SOURCE is an unfinished backup, refusing"; exit 1 ;; esac
[ -f "$DUMP" ] || { log "No database dump at $APP_DIR/$DUMP"; exit 1; }
gzip -t "$DUMP" || { log "$DUMP is damaged"; exit 1; }
gunzip -c "$DUMP" | tail -n 5 | grep -q "PostgreSQL database dump complete" \
    || { log "$DUMP is an incomplete dump, refusing"; exit 1; }

if [ "${2:-}" != "--yes" ]; then
    echo "This replaces the whole database${UPLOADS:+ and all posters} with $SOURCE."
    echo "Everything written since that backup is lost (a copy of the current state is kept first)."
    echo "Run again with --yes to go ahead."
    exit 1
fi

log "Saving the current state first"
if bash "$APP_DIR/backup.sh" pre-restore; then
    SAFETY="$(find backups/pre-restore -mindepth 1 -maxdepth 1 -type d ! -name '*.partial' | sort | tail -n 1)"
    log "Current state saved in $APP_DIR/$SAFETY"
else
    log "Could not back up the current state, nothing was changed"
    exit 1
fi

log "Stopping the backend"
docker compose stop backend

log "Recreating the database from $DUMP"
docker compose exec -T db sh -c \
    'dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
gunzip -c "$DUMP" | docker compose exec -T db sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q -v ON_ERROR_STOP=1 > /dev/null'

if [ -n "$UPLOADS" ]; then
    log "Replacing the posters from $UPLOADS"
    docker compose run --rm --no-deps -T --entrypoint sh backend \
        -c 'find /app/uploads -mindepth 1 -delete && tar xzf - -C /app/uploads' < "$UPLOADS"
else
    log "This backup has no posters: the current ones stay"
fi

log "Starting the backend"
docker compose up -d backend
deadline=$((SECONDS + HEALTH_TIMEOUT))
until curl -fsS --max-time 5 "$HEALTH_URL" 2> /dev/null | grep -q '"status":"UP"'; do
    if ((SECONDS >= deadline)); then
        log "The backend is not healthy after ${HEALTH_TIMEOUT}s: check 'docker compose logs backend'."
        log "To go back to the state before this restore: bash $APP_DIR/restore.sh $SAFETY --yes"
        exit 1
    fi
    sleep 3
done
log "Done: restored $SOURCE and the backend is healthy. The state before the restore is in $APP_DIR/$SAFETY"
