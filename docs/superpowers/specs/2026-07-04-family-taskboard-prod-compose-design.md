# Family Taskboard — Produktions-Docker-Compose — Design-Spezifikation

- **Datum:** 2026-07-04
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Aufbauend auf der CI/CD-Pipeline
  ([2026-07-04-family-taskboard-cicd-design.md](2026-07-04-family-taskboard-cicd-design.md)),
  die bei jedem Push auf `main` ein Image nach
  `ghcr.io/larsauswsw/family-taskboard` veröffentlicht. Dieses Spec beschreibt
  eine zweite Compose-Datei, die dieses veröffentlichte Image für den
  Produktivbetrieb nutzt, statt lokal zu bauen.

## 1. Überblick & Ziel

Eine neue Datei `docker-compose.prod.yml`, strukturell identisch zur
bestehenden `docker-compose.yml`, aber mit `image:
ghcr.io/larsauswsw/family-taskboard:latest` statt `build: .` — für den
Betrieb auf einem Produktionsserver, der Nginx Proxy Manager (separat, außerhalb
dieses Compose-Stacks) als Reverse-Proxy für HTTPS nutzt.

### Scope-Abgrenzung

**In Scope:**
- Neue Datei `docker-compose.prod.yml` (App-Service referenziert das
  ghcr.io-Image, DB-Service wie gehabt).
- `restart: unless-stopped` auf beiden Services.
- README-Ergänzung: kurze Anleitung für den Produktivbetrieb.

**Explizit nicht in Scope:**
- Reverse-Proxy/HTTPS (wird separat über Nginx Proxy Manager gelöst, läuft
  außerhalb dieses Compose-Stacks).
- Automatisches Deployment/Rollout auf den Zielserver (Pull+Restart bleibt
  ein manueller Schritt des Nutzers).
- Backup-Strategie für das Postgres-Volume.
- Änderungen an der bestehenden `docker-compose.yml` (bleibt unverändert für
  lokale Entwicklung).

## 2. `docker-compose.prod.yml`

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

### Abweichung von der bestehenden `docker-compose.yml`

Der `db`-Service veröffentlicht hier **keinen** Port 5432 auf den Host
(anders als in der bestehenden Datei, wo das für lokales Debugging praktisch
ist). In Produktion reduziert das die Angriffsfläche: Nginx Proxy Manager
braucht nur den App-Port 8080, nie einen direkten Datenbankzugriff von
außerhalb des Compose-Netzwerks.

### Netzwerk-Modell

`app` bleibt über den auf den Host veröffentlichten Port `8080` erreichbar
(kein gemeinsames externes Docker-Netzwerk mit Nginx Proxy Manager) — Nginx
Proxy Manager proxied auf diesen Host-Port, genau wie er es mit jedem anderen
Reverse-Proxy-Ziel auf demselben Host tun würde.

## 3. Nutzung (README-Ergänzung)

Ergänzung im README-Abschnitt "Running it", nach dem bestehenden
"Mit Docker (empfohlen)"-Unterabschnitt:

```markdown
### Produktivbetrieb (mit dem veröffentlichten Image von ghcr.io)

```bash
cp .env.example .env
# .env ausfüllen: DB_PASSWORD, VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Nutzt das bei jedem Push auf `main` automatisch gebaute Image von
`ghcr.io/larsauswsw/family-taskboard:latest` statt lokal zu bauen. Ein
Update auf die neueste Version: `docker compose -f docker-compose.prod.yml
pull && docker compose -f docker-compose.prod.yml up -d` erneut ausführen.
HTTPS wird hier nicht mitgeliefert — dafür einen eigenen Reverse-Proxy (z.B.
Nginx Proxy Manager) vor Port 8080 schalten.
```
```

## 4. Fehlerbehandlung & Edge Cases

- **Image-Pull schlägt fehl** (z.B. Netzwerkproblem, oder das Package wurde
  versehentlich wieder auf privat gestellt): `docker compose ... pull` bricht
  mit einer klaren Fehlermeldung ab; ein bereits laufender alter Container
  läuft in diesem Fall unverändert weiter — kein Sonderfall nötig,
  Standard-Docker-Compose-Verhalten. Rollback auf eine ältere Version ist ein
  bewusst manueller Schritt (einen SHA-Tag statt `:latest` referenzieren und
  erneut `up -d` ausführen), nicht automatisiert.
- **`restart: unless-stopped` bei Absturz während laufendem Datenbank-Write:**
  Postgres' eigene Crash-Recovery (WAL) übernimmt das, kein
  anwendungsspezifisches Verhalten nötig.
- **Erstes Passwort ändern:** unverändert aus der bestehenden Dokumentation
  (`BootStrap.groovy` seedet `lars`/`changeme`) — gilt identisch für den
  Produktivbetrieb, keine neue Anforderung hier.

## 5. Offene Punkte / Annahmen

- Kein automatisiertes Update/Watchtower-artiges Polling auf neue Images —
  der Nutzer pullt/restartet manuell, wenn gewünscht.
- Keine Backup-Strategie für das `dbdata`-Volume in dieser Spec — eigenes,
  späteres Thema, falls gewünscht.
