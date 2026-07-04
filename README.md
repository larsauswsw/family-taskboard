# Family Taskboard

A German-language task board for a family: shared tasks with due dates, assignees,
and priorities, where each task's color gets progressively more "dangerous"
(green → yellow → orange → red → darkred) the closer — or more overdue — it gets.

Built with **Grails 7.1.1** + **HTMX** (server-rendered, no SPA framework) on
**Java 21**, backed by **PostgreSQL**. Tasks can be added from the web UI (typed
or dictated via the browser's Web Speech API) or, for the fastest possible path,
via **Apple Shortcuts** (Siri, Back Tap, Action Button, Lock Screen widget) hitting
a small token-authenticated REST endpoint — see [docs/apple-shortcut.md](docs/apple-shortcut.md).

Full product spec: [docs/superpowers/specs/2026-06-30-family-taskboard-design.md](docs/superpowers/specs/2026-06-30-family-taskboard-design.md).
Implementation plan: [docs/superpowers/plans/2026-06-30-family-taskboard-phase1.md](docs/superpowers/plans/2026-06-30-family-taskboard-phase1.md).

## Running it

### With Docker (recommended)

```bash
cp .env.example .env
# edit .env: set DB_PASSWORD, and VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY for Web Push
docker compose up --build
```

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

```bash
export DB_USER=taskboard DB_PASSWORD=changeme DB_URL=jdbc:postgresql://localhost:5432/taskboard
./gradlew bootRun
```

### Locally, against the in-memory test database

Useful for quickly poking at the app without a real Postgres instance — uses the
same H2 config as the test suite:

```bash
./gradlew bootRun -Dgrails.env=test
```

### Tests

```bash
./gradlew test integrationTest
```

## Architecture

```
┌──────────────┐        ┌──────────────────────────┐        ┌────────────┐
│  Browser/PWA │──HTMX─▶│  Grails 7.1.1 app (Tomcat)│──JDBC─▶│ PostgreSQL │
└──────────────┘        │  GSP views, no SPA build  │        └────────────┘
                         └──────────────────────────┘
┌──────────────┐              ▲
│Apple Shortcut│──Bearer──────┘
│(Siri/Watch)  │  POST /api/tasks/quick
└──────────────┘
```

Two entry points for creating a task:
- **Web UI** (`/task/index`): an HTMX-driven page with a quick-add form and an
  in-page mic button (Web Speech API, `grails-app/assets/javascripts/voice.js`).
- **REST quick-add** (`POST /api/tasks/quick`, `ApiTaskController`): stateless,
  Bearer-token-authenticated (each `User` has an `apiToken`), designed to be hit
  from an Apple Shortcut so a task can be dictated straight from the Lock Screen,
  Watch, Siri, or Back Tap. CSRF is not applicable here since there's no session/cookie.

### Domain model

- **`User`** (`grails-app/domain/taskboard/User.groovy`) — implements Spring
  Security's `UserDetails` directly; always has `ROLE_USER`. Holds the per-user
  `apiToken` used by the quick-add endpoint, and per-user notification prefs
  (`notifyDaysBefore`, `notifyOnDueDate`, used by the not-yet-built scheduler).
- **`Task`** (`grails-app/domain/taskboard/Task.groovy`) — title, due date,
  `Priority` (LOW/MEDIUM/HIGH/CRITICAL, each with an urgency multiplier),
  `TaskStatus` (OPEN/IN_PROGRESS/DONE), optional assignee, and the user who
  created it.
- **`UrgencyConfig`** (`grails-app/domain/taskboard/UrgencyConfig.groovy`) — a
  singleton row holding the configurable day thresholds for each color band.
  `UrgencyService.colorFor(task, today, cfg)` divides "days until due" by the
  task's priority multiplier (so a CRITICAL task turns red faster than a LOW
  one with the same due date) and compares against the thresholds.

## Notable deviations from the original plan

The implementation plan assumed two Grails plugins that turned out to have no
usable stable release for Grails 7.1.1, so both were replaced with plain
Spring/Spring Boot equivalents:

- **No `spring-security-core` Grails plugin** → plain Spring Security 6 /
  Spring Boot 3 (`spring-boot-starter-security`). `User` implements `UserDetails`
  directly, `UserDetailsServiceImpl` implements Spring's `UserDetailsService`,
  and `src/main/groovy/taskboard/SecurityConfig.groovy` defines the
  `SecurityFilterChain` by hand. There are no `Role`/`UserRole` domains — every
  `User` just has `ROLE_USER`.
- **No `quartz` Grails plugin** (its 3.x pulls in an incompatible Groovy version)
  → the not-yet-built due-date reminder job will use Spring's built-in
  `@Scheduled`/`@EnableScheduling` instead.

### Gotchas specific to this setup (worth knowing before touching `SecurityConfig`)

Because Grails doesn't wire this the way a plain Spring Boot app would, a few
things that "should just work" don't, and are handled explicitly:

- **`SecurityConfig` needs `@Import(SecurityConfig)` on `Application.groovy`.**
  Grails component-scans `grails-app/**` artefacts, not arbitrary
  `@Configuration` classes under `src/main/groovy`. Without the explicit
  `@Import`, the bean is silently never created and Spring Boot Actuator's
  default security auto-configuration takes over instead, locking every
  request (including ones meant to be public) behind a generated HTTP Basic
  password.
- **The `AuthenticationProvider` is wired explicitly**, rather than relying on
  Spring Boot's automatic `UserDetailsService` detection. Grails registers our
  `userDetailsService` bean (from `grails-app/conf/spring/resources.groovy`)
  late enough that Boot's own `inMemoryUserDetailsManager` auto-configuration
  doesn't see it and gets created too — with two `UserDetailsService` beans
  present, Spring Security can't automatically build a working
  `AuthenticationManager`, and login fails silently. See the `authenticationProvider`
  bean and its comment in `SecurityConfig.groovy`.
- **CSRF uses the plain (non-XOR) request handler and a `CsrfCookieFilter`.**
  Spring Security 6's default `csrfTokenRequestHandler` masks the token per
  request for BREACH protection, which is incompatible with reading the raw
  `XSRF-TOKEN` cookie in JS and echoing it back as a header (what the HTMX
  quick-add form does, and what a JS-driven SPA does generally — this is
  Spring's own documented trade-off for that pattern). The `CsrfCookieFilter`
  forces the token to be resolved (and the cookie written) on every response,
  since Spring only writes it lazily when a view happens to render `${_csrf}`,
  and most of our pages never do.
- **CSRF is disabled only for `/api/**`** (the Bearer-token quick-add
  endpoint); every session/cookie-based route keeps CSRF protection.

## Repository layout

```
grails-app/
  domain/       GORM domain classes (User, Task, UrgencyConfig, ...)
  controllers/  TaskController (web UI), ApiTaskController (REST quick-add), LoginController
  services/     TaskService, UrgencyService, UserDetailsServiceImpl
  views/        GSP templates (task/index.gsp + HTMX fragments, login/auth.gsp)
  assets/       CSS (urgency colors) and JS (voice.js)
  init/         BootStrap.groovy (seeds a default user + UrgencyConfig)
src/main/groovy/taskboard/
  SecurityConfig.groovy   Spring Security filter chain, see gotchas above
src/test/, src/integration-test/
  Spock specs — unit tests are plain GORM/service specs; integration tests spin
  up the full embedded server and make real HTTP calls where the thing under
  test is the security/HTTP layer itself (login flow, CSRF, token auth)
docs/
  apple-shortcut.md                       how to wire up the iOS quick-add Shortcut
  superpowers/specs/, superpowers/plans/  original design spec and implementation plan
```

## Configuration

All runtime config is via environment variables (see `.env.example`):

| Variable | Purpose |
|---|---|
| `DB_USER`, `DB_PASSWORD`, `DB_URL` | PostgreSQL connection |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Web Push (no APNs certificates needed) |

No secrets are committed; `.env` is git-ignored.
