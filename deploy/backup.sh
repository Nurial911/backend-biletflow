#!/usr/bin/env bash
# Backs up the server's data (SRS section 7: regular backups): the database and the uploaded event posters.
# The pipeline copies this file to ~/app with deploy.sh. Restore with restore.sh; see deploy/BACKUPS.md.
#
#   bash ~/app/backup.sh nightly                   run by cron every night (installed by deploy.sh)
#   bash ~/app/backup.sh deploy before-<tag>       run by deploy.sh before every deploy
#   bash ~/app/backup.sh manual [label]            by hand, kept until deleted by hand
#   bash ~/app/backup.sh --install-schedule        (re)installs the nightly cron job; deploy.sh calls it
#
# Each backup is a folder backups/<kind>/<yyyymmdd-HHMMSS>[-label]/ with
#   db.sql.gz          pg_dump of the whole database (plain SQL, gzip)
#   uploads.tar.gz     the posters in the "uploads" volume
# It is written as <folder>.partial and renamed only once both files are complete and checked, so a
# folder without .partial is always a whole backup. Old ones are deleted: 14 nightly, 10 deploy.
#
# Exit codes: 0 backed up, 1 failed, 2 the database container isn't running (nothing to back up; deploy.sh
# treats that as a first deploy). The settings below can be overridden from the environment (for testing).
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/app}"
KEEP_NIGHTLY="${KEEP_NIGHTLY:-14}"
KEEP_DEPLOY="${KEEP_DEPLOY:-10}"
SCHEDULE_TAG="# biletflow-nightly-backup"

log() { echo "[backup $(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# 02:30 in Almaty (UTC+5) whatever the server's own time zone is: quiet hours for the people using the site.
install_schedule() {
    if ! command -v crontab > /dev/null; then
        log "crontab is not installed: nightly backups are NOT scheduled (install cron, then deploy again)"
        return 1
    fi
    local offset hours hour line
    offset="$(date +%z)"                       # e.g. +0000 on a UTC server
    hours=$((10#${offset:1:2}))
    [ "${offset:0:1}" = "-" ] && hours=$((-hours))
    hour=$(( (2 - 5 + hours + 48) % 24 ))
    mkdir -p "$APP_DIR/backups"
    line="30 $hour * * * bash $APP_DIR/backup.sh nightly >> $APP_DIR/backups/backup.log 2>&1 $SCHEDULE_TAG"
    { crontab -l 2> /dev/null | grep -vF "$SCHEDULE_TAG" || true; echo "$line"; } | crontab -
    log "Nightly backup scheduled at $hour:30 server time (02:30 Almaty): $line"
}

if [ "${1:-}" = "--install-schedule" ]; then
    install_schedule
    exit
fi

KIND="${1:?usage: backup.sh nightly | deploy <label> | manual [label] | --install-schedule}"
LABEL="${2:-}"
case "$KIND" in
    nightly) KEEP="$KEEP_NIGHTLY" ;;
    deploy) KEEP="$KEEP_DEPLOY" ;;
    manual | pre-restore) KEEP="" ;;           # kept until deleted by hand
    *) echo "unknown kind: $KIND" >&2; exit 1 ;;
esac

cd "$APP_DIR"
mkdir -p "backups/$KIND"

# One backup at a time (cron and a deploy could meet). flock is part of util-linux on the VM.
if command -v flock > /dev/null; then
    exec 9> backups/.lock
    flock -w 600 9 || { log "Another backup has been running for 10 minutes, giving up"; exit 1; }
fi

if ! docker compose ps --status running --services 2> /dev/null | grep -qx db; then
    log "The database container is not running: nothing to back up"
    exit 2
fi

name="$(date +%Y%m%d-%H%M%S)${LABEL:+-$LABEL}"
target="backups/$KIND/$name"
partial="$target.partial"
rm -rf "$partial"
mkdir -p "$partial"
trap 'rm -rf "$partial"' EXIT

# Single quotes: the variables are expanded inside the container, which has them from app.env.
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$partial/db.sql.gz"
# A dump that stopped half-way still gzips fine; pg_dump writes this line only when it finished.
if ! gzip -t "$partial/db.sql.gz" || ! gunzip -c "$partial/db.sql.gz" | tail -n 5 | grep -q "PostgreSQL database dump complete"; then
    log "The database dump is incomplete"
    exit 1
fi

# With the backend's own image, which mounts the volume and has tar, whether the app is running or not.
docker compose run --rm --no-deps -T --entrypoint tar backend czf - -C /app/uploads . > "$partial/uploads.tar.gz"
gzip -t "$partial/uploads.tar.gz" || { log "The posters archive is incomplete"; exit 1; }

mv "$partial" "$target"
trap - EXIT
log "Backed up to $APP_DIR/$target (database $(du -h "$target/db.sql.gz" | cut -f1), posters $(du -h "$target/uploads.tar.gz" | cut -f1))"

if [ -n "$KEEP" ]; then
    # Names start with the time, so they sort oldest first; .partial leftovers don't match */.
    find "backups/$KIND" -mindepth 1 -maxdepth 1 -type d ! -name '*.partial' | sort | head -n -"$KEEP" \
        | while read -r old; do
            rm -rf "$old" && log "Removed old backup $old"
        done
fi
