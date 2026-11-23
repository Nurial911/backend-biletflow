# Serving the frontend

The VM serves the web app as well as the API. The frontend (repository `alinaalibekova-coder/frontend-BiletFlow`)
is a React app that builds to static files, and Caddy serves those files on the API's own domain:

| Path | Served by |
|---|---|
| `/api/*` (including the Halyk callback), `/swagger-ui*`, `/v3/api-docs*`, `/actuator/*` | backend container |
| everything else | files in `~/app/frontend`; a path with no file gets `index.html`, so page routes, email links and bank-return links work when opened directly |

One domain means no CORS: the frontend calls `/api/...` on the address it was loaded from. `APP_FRONTEND_URL`
in `~/app/app.env` is that same address (`https://<DOMAIN>`), so links in emails, calendar entries, Campaign QR
codes and Halyk's return page open the live site.

`index.html` and other non-asset files are sent with `Cache-Control: no-cache`, so visitors see a new build
straight away. Files in `assets/` have a content hash in their names and never change.

## Updating the frontend

CI does not deploy the frontend. After a change is merged into the frontend's `main`, build it and copy it to
the VM. In a clean checkout of the frontend repository:

```bash
git checkout main && git pull
npm ci && npm test && npm run build
tar -C dist -cf - . | ssh <user>@<vm-host> 'tar -C ~/app/frontend -xf -'
```

Caddy serves the new files at once; nothing restarts. Old files in `assets/` are left behind, which keeps pages
that are already open working; delete them now and then if you like.

Build from `main` only, never from a working copy with uncommitted changes: what is on the VM should always be
a commit anyone can check out.

## First-time setup

Done on 2026-11-22. For a new VM:

1. `mkdir -p ~/app/frontend` and copy a build into it (above).
2. Copy `deploy/Caddyfile` and `deploy/docker-compose.yml` to `~/app` (the compose file mounts `./frontend`).
3. Set `APP_FRONTEND_URL=https://<DOMAIN>` in `~/app/app.env`.
4. `cd ~/app && docker compose up -d --force-recreate --no-deps caddy backend`. `--no-deps` keeps the database
   running; the backend is unavailable for about 30 seconds while it restarts.

## Undoing it

Restore the previous `Caddyfile`, `docker-compose.yml` and `app.env` (the setup saved copies in
`~/app/backups/config-20261122-232315/`), then run the command from step 4. `~/app/frontend` can stay or be
deleted; nothing reads it once Caddy no longer mounts it.
