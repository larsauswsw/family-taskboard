# Family Taskboard — CI/CD: Docker-Image-Publishing — Design-Spezifikation

- **Datum:** 2026-07-04
- **Repository:** https://github.com/larsauswsw/family-taskboard (öffentlich)
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Nutzer-Anfrage: bei jedem Push auf `main` automatisch ein Docker-Image
  bauen und nach `ghcr.io` (GitHub Container Registry) veröffentlichen.

## 1. Überblick & Ziel

Ein neuer GitHub-Actions-Workflow baut bei jedem Push auf `main` das bestehende
`Dockerfile` und veröffentlicht das Image nach `ghcr.io/larsauswsw/family-taskboard`
— aber nur, wenn die Test-Suite zuvor grün war.

### Scope-Abgrenzung

**In Scope:**
- Ein Workflow-File `.github/workflows/docker-publish.yml`.
- Test-Gate (`./gradlew test integrationTest --rerun-tasks`) vor dem Image-Build.
- Image-Tags: `latest` + Kurz-Commit-SHA.
- Plattform: `linux/amd64` (kein Multi-Arch).
- Push nach `ghcr.io` via `GITHUB_TOKEN`, keine zusätzlichen Secrets.

**Explizit nicht in Scope:**
- Automatisches Deployment auf einen Server (nur Image-Publishing, kein
  Rollout).
- Multi-Arch-Build (arm64).
- Docker-Layer-Caching (GitHub Actions Cache für `docker/build-push-action`).
- Semantic-Versioning/Release-Tags (nur `latest` + SHA, kein `v1.2.3`).
- Automatisches Umstellen der Package-Sichtbarkeit auf öffentlich (siehe §4 —
  dieser eine Schritt bleibt manuell).

## 2. Workflow-Datei

`.github/workflows/docker-publish.yml`, ausgelöst bei jedem Push auf `main`,
zwei sequenzielle Jobs:

```yaml
name: Build and Publish Docker Image

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle
      - name: Run tests
        run: ./gradlew test integrationTest --rerun-tasks

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/metadata-action@v5
        id: meta
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=raw,value=latest
            type=sha,format=short
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

### Test-Job

- Java 21 / Temurin, passend zu `Dockerfile`s Build-Stage
  (`eclipse-temurin:21-jdk`).
- `cache: gradle` (eingebaut in `actions/setup-java`) cached `~/.gradle`
  zwischen Läufen.
- `--rerun-tasks` erzwingt einen vollständigen Testlauf statt Gradles
  inkrementellem Caching — dieses Projekt ist laut
  `.superpowers/sdd/progress.md`-Ledger bereits mehrfach auf unvollständige
  "grüne" Läufe durch genau dieses Caching-Verhalten hereingefallen.
- Integrationstests laufen mit H2 (Grails' Standard-`test`-Umgebung) — kein
  Postgres-Service-Container in CI nötig.

### Build-and-Push-Job

- `needs: test` — läuft nur, wenn der Test-Job erfolgreich war.
- `permissions: packages: write` erlaubt dem eingebauten `GITHUB_TOKEN` den
  Push nach `ghcr.io` — kein zusätzliches Secret/PAT nötig.
- `${{ github.repository }}` ist bereits `larsauswsw/family-taskboard`, ergibt
  automatisch `ghcr.io/larsauswsw/family-taskboard` — kein Hardcoding.
- `docker/metadata-action` erzeugt die zwei Tags: `latest` und den Kurz-SHA
  (z.B. `:a1b2c3d`).
- `platforms: linux/amd64` — nur eine Plattform.
- Nutzt das bestehende `Dockerfile` unverändert.

## 3. Berechtigungen & Secrets

Keine neuen Secrets nötig — `secrets.GITHUB_TOKEN` wird von GitHub Actions
automatisch bereitgestellt und reicht für Login + Push nach `ghcr.io`, sofern
der Job explizit `permissions: packages: write` deklariert (Standard-Token
hat sonst nur Lesezugriff auf Packages).

## 4. Fehlerbehandlung & Edge Cases

- **Package-Sichtbarkeit (wichtigster Punkt):** GitHub erstellt ein neues
  Container-Package standardmäßig als **privat**, unabhängig von der
  Repo-Sichtbarkeit — das kann der `GITHUB_TOKEN` nicht selbst umstellen
  (fehlende Berechtigung dafür). Nach dem **ersten** erfolgreichen
  Workflow-Lauf muss einmalig manuell auf GitHub zu "Packages" →
  `family-taskboard` → "Package settings" → "Change visibility" → "Public"
  gewechselt werden. Danach bleibt es dauerhaft öffentlich, auch bei allen
  folgenden Pushes.
- **Testfehler blockieren den Push zuverlässig:** `needs: test` sorgt dafür,
  dass `build-and-push` bei fehlgeschlagenem Test-Job gar nicht erst startet
  (GitHub-Actions-Standardverhalten, kein Sonderfall nötig).
- **Überlappende Pushes:** bei mehreren Pushes kurz hintereinander laufen
  mehrere Workflow-Instanzen parallel — kein `concurrency`-Guard nötig, da das
  zuletzt fertige Ergebnis `:latest` überschreibt und ältere SHA-Tags
  unabhängig bestehen bleiben.

## 5. Offene Punkte / Annahmen

- Kein automatisches Deployment/Rollout auf einen Zielserver — dieses Design
  deckt nur das Bauen und Veröffentlichen des Images ab. Ein Server müsste das
  neue Image weiterhin manuell (oder über ein separates, späteres Setup)
  pullen.
- Docker-Layer-Caching (z.B. `cache-from`/`cache-to` mit GitHub Actions Cache)
  bewusst weggelassen — bei Bedarf später nachrüstbar, aber für dieses
  Projekt mit seltenen Pushes kein spürbarer Vorteil, der die zusätzliche
  Komplexität rechtfertigt.
