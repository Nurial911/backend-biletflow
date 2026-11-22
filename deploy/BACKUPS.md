# Backups and recovery

SRS section 7: "Regular backups and recovery procedures shall be established." This is how the server does it.

## What is backed up, and when

| When | Made by | Kept | Folder on the VM |
|---|---|---|---|
| Every night, 02:30 Almaty time | cron, running `~/app/backup.sh nightly` | newest 14 | `~/app/backups/nightly/` |
| Before every deploy | `deploy.sh`, running `backup.sh deploy before-<commit>` | newest 10 | `~/app/backups/deploy/` |
| Before every restore | `restore.sh` | until deleted by hand | `~/app/backups/pre-restore/` |
| By hand | `bash ~/app/backup.sh manual [label]` | until deleted by hand | `~/app/backups/manual/` |

Each backup is one folder named after its time, for example `backups/nightly/20261123-023000/`, holding:

- `db.sql.gz`: `pg_dump` of the whole database (every table, including Flyway's history), plain SQL, gzipped.
- `uploads.tar.gz`: the event posters from the `uploads` volume. Everything else the app needs is in the database
  or in git.

Not backed up: `~/app/app.env` (the secrets; keep your own copy somewhere safe, outside git) and Caddy's HTTPS
certificates (Caddy simply requests new ones).

A backup is written as `<folder>.partial` and renamed only when both files are complete: the dump must end with
pg_dump's "dump complete" line and both archives must pass `gzip -t`. So a folder without `.partial` is always a
whole backup, and a failed run leaves nothing behind. Every nightly run is logged in `~/app/backups/backup.log`.

The nightly job is installed by `deploy.sh` on every deploy (the line ends in `# biletflow-nightly-backup`), so
a rebuilt server gets it back with the next deploy. The pipeline copies `deploy.sh`, `backup.sh` and `restore.sh`
to `~/app` each time.

## Checking that backups run

```bash
ssh <vm>
crontab -l | grep biletflow-nightly-backup     # the schedule
tail -n 5 ~/app/backups/backup.log             # "Backed up to ..." lines, one per night
ls -l ~/app/backups/nightly/                    # up to 14 folders
```

## Restoring

`restore.sh` replaces the **whole** database (and the posters, when the backup has them) with a backup. Everything
written since that backup is lost, so first decide which backup you want:

```bash
ls ~/app/backups/nightly ~/app/backups/deploy
bash ~/app/restore.sh backups/nightly/20261123-023000          # explains what it will do, changes nothing
bash ~/app/restore.sh backups/nightly/20261123-023000 --yes    # does it
```

What it does:

1. Backs up the current state to `backups/pre-restore/`, so the restore itself can be undone.
2. Stops the backend, so nothing writes during the restore.
3. Drops and recreates the database and loads the dump.
4. Replaces the posters.
5. Starts the backend and waits until `/actuator/health` answers UP. If that doesn't happen within 2 minutes, it
   prints the command that puts back the state from step 1.

It refuses a damaged dump, an unfinished `.partial` backup, and anything without `--yes`.

An older backup is fine with a newer app: on start, Flyway applies the migrations the dump doesn't have yet.
The other way round (a newer backup with an older app) doesn't work: deploy the matching version first
(the folder name says which commit a deploy backup was taken before).

Single `.sql.gz` files from before this setup (`backups/db-<time>-before-<commit>.sql.gz`) can be restored the
same way; they hold only the database, so the posters stay as they are.

## Restoring onto another machine

If the VM itself is lost, the backups on its disk are lost with it. To keep a copy elsewhere, copy the newest
backup off the server now and then:

```bash
scp -r <vm>:app/backups/nightly/$(ssh <vm> 'ls ~/app/backups/nightly | tail -n 1') ./biletflow-backup/
```

To restore on a new server: set up `~/app` as for a first deploy (`docker-compose.yml`, `Caddyfile`, `.env`,
`app.env`), start it once with `docker compose up -d`, copy the backup folder to `~/app/backups/`, and run
`restore.sh` on it as above.

## How this was tested

`backup.sh`, `restore.sh` and `deploy.sh` were run against a throwaway Docker Compose stack with the same
service and volume names as the server: PostgreSQL 16 with BiletFlow's real schema (migrations V1 to V7) and a
stand-in backend serving the health URL. The test, on 2026-11-22:

- made a nightly backup and checked that the dump held the test data and the archive held both posters;
- changed the data and posters, restored, and found the database, the V7 constraints and the posters exactly as
  backed up, the backend healthy, and the pre-restore copy holding the changed state;
- checked that restore refuses without `--yes`, a damaged dump and a `.partial` folder;
- checked that only the newest nightly backups are kept and pre-restore ones never removed;
- restored an old single-file dump;
- installed the cron job twice (one line, other jobs kept) and checked it runs at 02:30 Almaty time for
  server time zones UTC, UTC+3, UTC+5 and UTC−4;
- ran `deploy.sh`, which backed up into `backups/deploy/` and installed the cron job;
- checked that `backup.sh` reports exit code 2 when the database isn't running.

ShellCheck reports nothing for the three scripts. Not tested on the server itself: the first real nightly
run. Check `backup.log` the morning after the first deploy with these scripts.
