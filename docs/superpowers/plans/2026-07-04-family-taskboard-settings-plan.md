# Settings Page (API Token) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user view their own username and API token, and regenerate the token (with a confirmation warning), from a new `/settings` page — replacing the current "read it directly from the database" workaround documented in `docs/apple-shortcut.md`.

**Architecture:** A new `UserService.regenerateApiToken(User user)` (mirrors the established `ProjectService`/`TaskService` pattern: a service method that mutates and saves, called from a controller) generates a fresh 8-character alphanumeric token via `SecureRandom`. A new `SettingsController` (`GET /settings` shows the page, `POST /settings/regenerateToken` regenerates and redirects back) renders a plain, full-page GSP view — not an HTMX fragment, since this page is not part of the task-list SPA. A navbar link on the task page and an update to `docs/apple-shortcut.md` make the new page discoverable and documented.

**Tech Stack:** Grails 7.1.1, GORM, Spock (unit + `@Integration`), `java.security.SecureRandom` — no new dependencies.

## Global Constraints

- Grails 7.1.1 / Java 21 / Spock — same stack as prior features, no version changes.
- New tokens are exactly 8 characters, drawn from the alphanumeric set `A-Za-z0-9` (62 characters), generated with `java.security.SecureRandom` — a deliberate trade-off (less entropy than the previous 36-character UUID format) accepted for easier manual entry in a private, self-hosted, single-family app. Existing longer UUID-format tokens remain valid; `User.apiToken` has no format constraint.
- `regenerateApiToken()` always replaces the token unconditionally — no confirmation logic lives in the service; confirmation is a client-side (browser `confirm()`) concern in the view only.
- The settings page is a full server-rendered page (not an HTMX fragment). CSRF is handled via a hidden `_csrf` form field, exactly matching the existing pattern in `grails-app/views/login/auth.gsp` — not the HTMX header-echo shim used on the task list page.
- `SettingsController` has no `@Secured` annotation; access control is entirely via `SecurityConfig`'s `anyRequest().authenticated()` rule (already in place, `/settings/**` is not in the `permitAll` matcher list — no `SecurityConfig` changes needed).
- Out of scope (do not implement): editable notification settings (`notifyDaysBefore`/`notifyOnDueDate`), changing username/displayName/password, multi-user/admin management.

---

### Task 1: `UserService.regenerateApiToken`

**Files:**
- Create: `grails-app/services/taskboard/UserService.groovy`
- Test: `src/integration-test/groovy/taskboard/UserServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: nothing new (uses the existing `User` domain class).
- Produces: `UserService.regenerateApiToken(User user) -> User` — replaces `user.apiToken` with a fresh 8-character alphanumeric string and saves with `failOnError: true`, returning the same (now-mutated) `User` instance.

- [ ] **Step 1: Write the failing integration tests**

Create `src/integration-test/groovy/taskboard/UserServiceIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Integration
@Rollback
class UserServiceIntegrationSpec extends Specification {

    UserService userService

    void "regenerateApiToken replaces the token with a new 8-character alphanumeric value"() {
        given:
        def u = new User(username: "settings-u1", password: "p",
            displayName: "U", apiToken: "old-token-uuid-format").save(flush: true)

        when:
        def result = userService.regenerateApiToken(u)

        then:
        result.apiToken != "old-token-uuid-format"
        result.apiToken.length() == 8
        result.apiToken ==~ /[A-Za-z0-9]{8}/
        User.get(u.id).apiToken == result.apiToken
    }

    void "regenerateApiToken generates a different token on each call"() {
        given:
        def u = new User(username: "settings-u2", password: "p",
            displayName: "U", apiToken: "seed").save(flush: true)

        when:
        String first = userService.regenerateApiToken(u).apiToken
        String second = userService.regenerateApiToken(u).apiToken

        then:
        first != second
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.UserServiceIntegrationSpec"`
Expected: compilation FAILURE — `UserService` doesn't exist yet.

- [ ] **Step 3: Implement `UserService.groovy`**

Create `grails-app/services/taskboard/UserService.groovy`:

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import java.security.SecureRandom

/** Own-account operations on User -- currently just API token regeneration
 *  (see docs/superpowers/specs/2026-07-04-family-taskboard-settings-design.md). */
@Transactional
class UserService {

    private static final String TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    private static final int TOKEN_LENGTH = 8
    private static final SecureRandom RANDOM = new SecureRandom()

    /** Replaces the user's API token with a fresh 8-character random one,
     *  immediately invalidating the old one -- any Apple Shortcut using the
     *  previous token starts getting 401s until updated with the new value.
     *  Short and alphanumeric by deliberate choice (manual entry is easier
     *  than a 36-char UUID); SecureRandom is used despite the short length
     *  since this is still a bearer credential. No confirmation/undo logic
     *  here -- that's a client-side (browser confirm()) concern only. */
    User regenerateApiToken(User user) {
        user.apiToken = (1..TOKEN_LENGTH).collect { TOKEN_CHARS[RANDOM.nextInt(TOKEN_CHARS.size())] }.join()
        user.save(failOnError: true)
        user
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.UserServiceIntegrationSpec"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite to confirm nothing broke**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL — all unit and integration specs pass. Use `--rerun-tasks` for an authoritative total; a plain run undercounts due to Gradle's incremental test-task caching (established in this project's ledger after the recurring-tasks feature).

- [ ] **Step 6: Commit**

```bash
git add grails-app/services/taskboard/UserService.groovy src/integration-test/groovy/taskboard/UserServiceIntegrationSpec.groovy
git commit -m "feat: add UserService.regenerateApiToken"
```

---

### Task 2: `SettingsController` + view + navbar link + docs update

**Files:**
- Create: `grails-app/controllers/taskboard/SettingsController.groovy`
- Create: `grails-app/views/settings/show.gsp`
- Modify: `grails-app/views/task/index.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Modify: `docs/apple-shortcut.md`
- Test: `src/integration-test/groovy/taskboard/SettingsControllerIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `UserService.regenerateApiToken(User user) -> User` (Task 1).
- Produces: `GET /settings` (renders `show.gsp` with model `[user: User]`), `POST /settings/regenerateToken` (calls the service, redirects to `show`). No new interfaces for later tasks — this is the final task.

- [ ] **Step 1: Write the failing integration test**

Create `src/integration-test/groovy/taskboard/SettingsControllerIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Exercises SettingsController over real HTTP, same login-flow pattern as
 * ProjectControllerIntegrationSpec. No @Rollback: these HTTP calls run on a
 * separate server thread, so a test-level transaction wouldn't see them
 * anyway. Uses the seeded 'lars' user (from BootStrap) directly rather than
 * creating a new one, since this controller only ever acts on "the current
 * user" -- the token is restored in `cleanup:` so other tests/manual use of
 * the seeded user aren't disrupted afterwards.
 */
@Integration
class SettingsControllerIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    private static String cookieHeader(Map<String, String> cookies) {
        cookies.collect { k, v -> "${k}=${v}" }.join('; ')
    }

    private static Map<String, String> extractCookies(URLConnection conn, Map<String, String> into) {
        conn.headerFields.get('Set-Cookie')?.each { String raw ->
            def pair = raw.split(';')[0].split('=', 2)
            if (pair.length == 2) into[pair[0]] = pair[1]
        }
        into
    }

    private Map<String, String> loggedInCookies() {
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        extractCookies(initial, cookies)

        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookieHeader(cookies))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=changeme&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        login.responseCode
        extractCookies(login, cookies)

        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)
        cookies
    }

    void "unauthenticated request to /settings redirects to login"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/settings").openConnection()
        conn.instanceFollowRedirects = false

        expect:
        conn.responseCode == 302
        conn.getHeaderField("Location")?.contains("/login/auth")
    }

    void "shows username and current token, then regenerating replaces the token shown on the page"() {
        given:
        Map<String, String> cookies = loggedInCookies()
        String originalToken
        User.withTransaction { originalToken = User.findByUsername("lars").apiToken }

        when: "loading the settings page"
        def show = new URL("http://localhost:${serverPort}/settings").openConnection()
        show.setRequestProperty("Cookie", cookieHeader(cookies))
        int showStatus = show.responseCode
        String showBody = show.inputStream.text
        extractCookies(show, cookies)

        then: "it shows the username and the current token"
        showStatus == 200
        showBody.contains("lars")
        showBody.contains(originalToken)

        when: "regenerating the token, submitting _csrf as a form field exactly like the login form does"
        def regenerate = new URL("http://localhost:${serverPort}/settings/regenerateToken").openConnection()
        regenerate.requestMethod = "POST"
        regenerate.doOutput = true
        regenerate.instanceFollowRedirects = false
        regenerate.setRequestProperty("Cookie", cookieHeader(cookies))
        regenerate.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfParam = cookies['XSRF-TOKEN']
        regenerate.outputStream.withWriter { it << "_csrf=${URLEncoder.encode(csrfParam, 'UTF-8')}" }
        int regenerateStatus = regenerate.responseCode
        extractCookies(regenerate, cookies)

        then: "it redirects back to the settings page"
        regenerateStatus == 302

        when: "loading the settings page again"
        def after = new URL("http://localhost:${serverPort}/settings").openConnection()
        after.setRequestProperty("Cookie", cookieHeader(cookies))
        String afterBody = after.inputStream.text

        then: "the shown token is different from the original"
        !afterBody.contains(originalToken)

        cleanup: "restore a known token so other tests/manual use of the seeded user aren't disrupted"
        User.withTransaction {
            def lars = User.findByUsername("lars")
            lars.apiToken = originalToken
            lars.save(flush: true, failOnError: true)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.SettingsControllerIntegrationSpec"`
Expected: FAILURE — 404, `SettingsController` doesn't exist yet.

- [ ] **Step 3: Implement `SettingsController.groovy`**

Create `grails-app/controllers/taskboard/SettingsController.groovy`:

```groovy
package taskboard

import org.springframework.security.core.context.SecurityContextHolder

/**
 * Own-account settings: view API token, regenerate it. No @Secured
 * annotation -- covered by SecurityConfig's anyRequest().authenticated(),
 * same as TaskController/ProjectController.
 */
class SettingsController {

    static defaultAction = 'show'

    UserService userService

    /** There is no springSecurityService bean here; UserDetailsServiceImpl
     *  returns User instances directly, so the principal already IS a User. */
    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def show() {
        [user: currentUser()]
    }

    /** Redirect-after-POST avoids re-submitting the token regeneration if the
     *  resulting page is reloaded. */
    def regenerateToken() {
        userService.regenerateApiToken(currentUser())
        redirect action: 'show'
    }
}
```

- [ ] **Step 4: Implement `show.gsp`**

Create `grails-app/views/settings/show.gsp`:

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Einstellungen</title>
    <asset:stylesheet src="taskboard.css"/>
</head>
<body>
    <header class="navbar">
        <h1>Einstellungen</h1>
        <a href="${createLink(controller: 'task')}">← Zurück</a>
    </header>

    <main class="settings-page">
        <p><strong>Benutzername:</strong> ${user.username}</p>
        <p><strong>API-Token:</strong></p>
        <input type="text" readonly value="${user.apiToken}" class="token-display"
               onclick="this.select()">

        <form method="post" action="${createLink(action: 'regenerateToken')}"
              onsubmit="return confirm('Der alte Token wird sofort ungültig. Bestehende Apple-Shortcuts funktionieren erst wieder nach Aktualisierung. Fortfahren?')">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <button type="submit">Token neu generieren</button>
        </form>
    </main>
</body>
</html>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.SettingsControllerIntegrationSpec"`
Expected: PASS (2 tests).

- [ ] **Step 6: Add the navbar link**

In `grails-app/views/task/index.gsp`, replace:

```gsp
    <header class="navbar"><h1>Meine Tasks</h1></header>
```

with:

```gsp
    <header class="navbar">
        <h1>Meine Tasks</h1>
        <a href="${createLink(controller: 'settings')}" aria-label="Einstellungen">⚙️</a>
    </header>
```

- [ ] **Step 7: Add settings-page styles**

Append to `grails-app/assets/stylesheets/taskboard.css`:

```css
.settings-page { padding: 12px; }
.token-display { font-family: monospace; font-size: 1.1em; padding: 8px;
    width: 100%; max-width: 320px; box-sizing: border-box; margin-bottom: 12px; }
```

- [ ] **Step 8: Update `docs/apple-shortcut.md`**

In `docs/apple-shortcut.md`, replace the "1. Get Your API Token" section:

```markdown
### 1. Get Your API Token

There is no settings page for this yet (Phase 1 doesn't include one). Each
`User` row has an `apiToken` seeded randomly on creation (see
`grails-app/init/taskboard/BootStrap.groovy`); for now, read it directly from
the database, e.g.:

```sql
SELECT username, api_token FROM app_user WHERE username = 'lars';
```
```

with:

```markdown
### 1. Get Your API Token

Log in to Taskboard and open **⚙️ Einstellungen** (top of the task list) --
your API token is shown there, ready to copy. If you ever need to invalidate
it (e.g. it leaked, or you just want a fresh one), click **"Token neu
generieren"**; any Shortcut still using the old value will start getting 401
errors until you paste in the new one.
```
```

- [ ] **Step 9: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL — every unit and integration spec passes, including all pre-existing specs from every prior feature.

- [ ] **Step 10: Manually verify via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, then in a browser:
1. Log in as `lars`/`changeme`.
2. Click the ⚙️ link in the navbar — confirm the settings page loads, showing username "lars" and a token.
3. Click "Token neu generieren" — confirm a browser confirm dialog appears with the warning text.
4. Confirm it — confirm the page reloads and shows a new, different 8-character token.
5. Click "← Zurück" — confirm it returns to the task list.

- [ ] **Step 11: Commit**

```bash
git add grails-app/controllers/taskboard/SettingsController.groovy grails-app/views/settings/show.gsp grails-app/views/task/index.gsp grails-app/assets/stylesheets/taskboard.css docs/apple-shortcut.md src/integration-test/groovy/taskboard/SettingsControllerIntegrationSpec.groovy
git commit -m "feat: add settings page to view and regenerate the API token"
```
