#!/usr/bin/env bash
# Deploys one image tag on the VM. The pipeline copies this file to ~/app and runs:
#   bash ~/app/deploy.sh <image tag>
#
# 1. Backs up the database and posters (backup.sh) and makes sure the nightly backup is scheduled.
# 2. Saves the current TAG, writes the new one to ~/app/.env, pulls the images and restarts.
# 3. Waits for http://localhost:8080/actuator/health to answer UP.
# 4. Not healthy in time: puts the previous TAG back, restarts it and exits with an error, which
#    fails the pipeline. Healthy: removes old images.
#
# The settings below can be overridden from the environment (used for testing the script locally).
set -euo pipefail

NEW_TAG="${1:?usage: deploy.sh <image tag>}"
APP_DIR="${APP_DIR:-$HOME/app}"
IMAGE="${IMAGE:-ghcr.io/nurial911/backend-biletflow}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-90}"   # seconds; the app takes about 25 s to start on the VM

log() { echo "[deploy $(date +%H:%M:%S)] $*"; }

cd "$APP_DIR"
for file in docker-compose.yml app.env backup.sh; do
    [ -f "$file" ] || { log "Missing $APP_DIR/$file (templates are in deploy/ in the repo)"; exit 1; }
done
touch .env

# .env also holds DOMAIN=..., so only the TAG line is read and replaced.
current_tag() { sed -n 's/^TAG=//p' .env | tail -n 1; }
set_tag() {
    grep -v '^TAG=' .env > .env.tmp || true
    printf 'TAG=%s\n' "$1" >> .env.tmp
    mv .env.tmp .env
}

healthy() { curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; }

wait_until_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT))
    until healthy; do
        if (( SECONDS >= deadline )); then
            return 1
        fi
        sleep 3
    done
}

# backup.sh keeps the last 10 of these in backups/deploy/ (see deploy/BACKUPS.md).
backup_database() {
    local status=0
    bash "$APP_DIR/backup.sh" deploy "before-${NEW_TAG:0:12}" || status=$?
    case "$status" in
        0) ;;
        2) log "Database container is not running, no backup (first deploy?)" ;;
        *) log "Backup failed, nothing was changed"; exit 1 ;;
    esac
}

# Every deploy (re)installs the nightly cron job, so a rebuilt server gets it back by itself.
schedule_backups() {
    bash "$APP_DIR/backup.sh" --install-schedule || log "WARNING: the nightly backup is not scheduled"
}

rollback() {
    log "Deploy of $NEW_TAG failed: $1"
    docker compose logs --tail 100 backend || true
    if [ -z "$PREVIOUS_TAG" ]; then
        log "No previous tag to go back to"
        exit 1
    fi
    log "Rolling back to $PREVIOUS_TAG"
    set_tag "$PREVIOUS_TAG"
    if docker compose up -d && wait_until_healthy; then
        log "Rolled back: $PREVIOUS_TAG is running and healthy"
    else
        log "The previous version $PREVIOUS_TAG is not healthy either: check the server"
    fi
    exit 1
}

PREVIOUS_TAG="$(current_tag)"
log "Deploying $NEW_TAG (previous: ${PREVIOUS_TAG:-none})"

backup_database
schedule_backups
set_tag "$NEW_TAG"
docker compose pull || rollback "could not pull the images"
docker compose up -d || rollback "could not start the containers"
log "Waiting up to ${HEALTH_TIMEOUT}s for $HEALTH_URL"
wait_until_healthy || rollback "not healthy after ${HEALTH_TIMEOUT}s"
log "Healthy: $NEW_TAG is live"

# Removes untagged image layers left over from earlier pulls.
docker image prune -f
# Images of older commits still have their tags, so prune doesn't touch them. Keep the running one and
# the previous one (for a quick rollback), remove the rest.
docker images "$IMAGE" --format '{{.Tag}}' \
    | grep -vx -e "$NEW_TAG" -e "${PREVIOUS_TAG:-$NEW_TAG}" -e latest -e '<none>' \
    | while read -r tag; do
        docker rmi "$IMAGE:$tag" > /dev/null && log "Removed old image $IMAGE:$tag"
    done || true
log "Done"
