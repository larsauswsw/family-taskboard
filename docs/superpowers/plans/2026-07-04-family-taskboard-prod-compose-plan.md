# Production Docker Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `docker-compose.prod.yml` that runs the app from the published `ghcr.io/larsauswsw/family-taskboard:latest` image instead of building locally, for production use behind an externally-run Nginx Proxy Manager.

**Architecture:** A single new Compose file, structurally identical to the existing `docker-compose.yml` except `app` uses `image:` instead of `build: .`, both services get `restart: unless-stopped`, and the `db` service does not publish port 5432 to the host. A short README section documents how to use it.

**Tech Stack:** Docker Compose v2 — no new dependencies.

## Global Constraints

- `docker-compose.prod.yml` lives at the repo root, alongside the existing `docker-compose.yml` (which stays completely unmodified — it remains the local-development file that builds from source).
- `app` service image: exactly `ghcr.io/larsauswsw/family-taskboard:latest`.
- Both services (`app`, `db`) get `restart: unless-stopped`.
- `db` does NOT publish port 5432 to the host (deliberate deviation from `docker-compose.yml`, which does — see design spec §2).
- `app` DOES publish port 8080 to the host (`"8080:8080"`), same as `docker-compose.yml` — Nginx Proxy Manager (run separately, outside this stack) proxies to this host port.
- Same environment variables as `docker-compose.yml`: `DB_USER`, `DB_PASSWORD`, `DB_URL`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`, all sourced from `.env` (same `.env.example` as already exists — no changes to it needed).
- No reverse-proxy/HTTPS service in this Compose file — explicitly out of scope, handled separately by the user's own Nginx Proxy Manager setup.
- No TDD red/green cycle applies (this is a static config file, not application code). Verification is: (1) a local `docker compose config` validation that doesn't require a running Docker daemon, (2) a real pull-and-start smoke test against the actual published image.

---

### Task 1: `docker-compose.prod.yml` + README section

**Files:**
- Create: `docker-compose.prod.yml`
- Modify: `README.md` (insert a new subsection after the existing "With Docker (recommended)" subsection, before "### Locally, against Postgres")

**Interfaces:**
- Consumes: the already-published `ghcr.io/larsauswsw/family-taskboard:latest` image (built by the existing `.github/workflows/docker-publish.yml` CI pipeline) and the existing `.env.example` file (unchanged, same variable names).
- Produces: nothing consumed by later tasks — this plan has only one task.

- [ ] **Step 1: Create `docker-compose.prod.yml`**

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: taskboard
    volumes:
      - dbdata:/var/lib/postgresql/data
    restart: unless-stopped
  app:
    image: ghcr.io/larsauswsw/family-taskboard:latest
    depends_on:
      - db
    environment:
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      DB_URL: jdbc:postgresql://db:5432/taskboard
      VAPID_PUBLIC_KEY: ${VAPID_PUBLIC_KEY}
      VAPID_PRIVATE_KEY: ${VAPID_PRIVATE_KEY}
      VAPID_SUBJECT: ${VAPID_SUBJECT}
    ports:
      - "8080:8080"
    restart: unless-stopped
volumes:
  dbdata:
```

- [ ] **Step 2: Validate the Compose file locally (no running Docker daemon required)**

`docker compose config` parses and merges the Compose file without needing a
running daemon — use this to catch YAML/schema errors before ever touching
the real registry. Since the file references `${...}` variables, supply
throwaway values inline for this syntax-only check (they don't need to be
real credentials — `config` doesn't connect to anything):

Run:
```bash
DB_USER=x DB_PASSWORD=y VAPID_PUBLIC_KEY= VAPID_PRIVATE_KEY= VAPID_SUBJECT=mailto:a@b.c \
  docker compose -f docker-compose.prod.yml config
```
Expected: prints the fully-resolved merged YAML (image names, env vars substituted, volumes section) with no error. A non-zero exit code or a "yaml: ..." parse error means the file is malformed — fix and re-run.

- [ ] **Step 3: Add the README section**

In `README.md`, find this existing block (the end of the "With Docker (recommended)" subsection):

```markdown
The app is then reachable at `http://localhost:8080`. On first start it seeds a
user `lars` with password `changeme` (see `grails-app/init/taskboard/BootStrap.groovy`) —
change this password for anything beyond local testing.

### Locally, against Postgres
```

Replace it with (inserting a new subsection between the two):

```markdown
The app is then reachable at `http://localhost:8080`. On first start it seeds a
user `lars` with password `changeme` (see `grails-app/init/taskboard/BootStrap.groovy`) —
change this password for anything beyond local testing.

### Produktivbetrieb (mit dem veröffentlichten Image von ghcr.io)

```bash
cp .env.example .env
# .env ausfüllen: DB_PASSWORD, VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Nutzt das bei jedem Push auf `main` automatisch gebaute Image von
`ghcr.io/larsauswsw/family-taskboard:latest` statt lokal zu bauen. Ein Update
auf die neueste Version: den Pull-und-Up-Befehl erneut ausführen. HTTPS wird
hier nicht mitgeliefert — dafür einen eigenen Reverse-Proxy (z.B. Nginx Proxy
Manager) vor Port 8080 schalten.

### Locally, against Postgres
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.prod.yml README.md
git commit -m "docs: add production docker-compose using the published ghcr.io image"
```

- [ ] **Step 5: Real smoke test — pull and start the actual published image**

This requires a running Docker daemon (Docker Desktop or equivalent started).
Check first:

Run: `docker info`
Expected: prints daemon info without error. If it fails with "Cannot connect to the Docker daemon", start Docker Desktop (or the local daemon) before continuing — this step cannot be skipped or faked, it's the real verification that the published image actually works standalone.

Then, using the repo's real `.env` (create one from `.env.example` if none exists locally, filling in a throwaway `DB_PASSWORD` — the VAPID keys can stay blank for this smoke test, push just won't be configured):

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```
Expected: both commands succeed; `pull` shows it downloading the `postgres:16` and `ghcr.io/larsauswsw/family-taskboard:latest` images (or "up to date" if already cached from earlier verification); `up -d` reports both containers started.

- [ ] **Step 6: Confirm the app actually responds**

Run: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/login/auth`
Expected: `200` (the login page). Give the app a few seconds to finish starting if you see a connection-refused error immediately after `up -d` — Grails/Spring Boot startup inside the container takes a little time.

- [ ] **Step 7: Tear down the smoke-test containers**

```bash
docker compose -f docker-compose.prod.yml down
```
Expected: both containers stop and are removed. This leaves the `dbdata` volume in place (matching Compose's default `down` behavior, which never deletes volumes unless `-v` is passed) — fine for a smoke test, nothing to clean up further.
