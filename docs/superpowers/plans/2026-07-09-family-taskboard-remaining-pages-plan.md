# Login, Settings & Projekt-Verwaltung Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Login, Settings, and Projekt-Verwaltung to the same tokenized visual standard as the task list, extract Projekt-Verwaltung into its own page, replace emoji navbar icons with flat SVG icons, and fix the manual dark-mode preference not applying outside the task list.

**Architecture:** Two new shared GSP templates (`grails-app/views/common/_themeInit.gsp`, `grails-app/views/common/_navbar.gsp`) get built first and wired into the task list (the one page that already has a working navbar/toggle today), then reused by the newly-extracted Projekt page and the polished Settings page. Login gets a full rewrite using only the tokens and `_themeInit.gsp` (no navbar, per design). All four tasks are additive/replacement CSS+GSP+one-controller-method changes — no domain/service changes.

**Tech Stack:** Grails 7.1.1 GSP, plain CSS with existing custom-property tokens from `_tokens.css`, vanilla JS — no new runtime dependencies.

## Global Constraints

- No new colors/fonts — every value used must already exist in `_tokens.css` (`--color-bg`, `--color-surface`, `--color-text`, `--color-text-muted`, `--color-pill-bg`, `--color-urgency-red`, `--shadow-card`, etc.) or be a plain structural value (padding, radius) consistent with the existing `taskboard.css` component rules.
- Navbar icons are flat, single-color inline SVGs: `fill="none" stroke="currentColor"`, `viewBox="0 0 24 24"`. Exact paths are given in Task 1 — copy verbatim, do not redraw.
- The theme toggle keeps swapping between a moon icon (shown when the effective theme is light — click switches to dark) and a sun icon (shown when the effective theme is dark — click switches to light), same as today's emoji version, just as SVGs shown/hidden via `display:none` instead of `textContent` swaps.
- All 3 navbar icons (folder/theme/gear) always render on all 3 navbar-bearing pages (task list, Settings, Projekt), including a link to the page you're already on — never conditionally hidden.
- The page `<h1>` is a link back to the task list (`createLink(controller: 'task')`) on every navbar page except the task list itself, via the navbar template's `linkHome` parameter.
- This project has no controller-level unit-test convention — all controller behavior is tested via `@Integration` specs doing real HTTP requests (see `src/integration-test/groovy/taskboard/*IntegrationSpec.groovy` for the established `loggedInCookies()`/raw-`URLConnection` pattern). Task 4's login-error test follows this pattern, not a `ControllerUnitTestMixin` unit test.
- Full-suite regression command after every task: `./gradlew test integrationTest --rerun-tasks`. Known baseline going into this plan: 116 tests, 0 failures. Verify the TRUE merged total via `build/reports/tests/index.html`'s counter divs (not console prose) — do not report a single suite's subtotal as the combined total.
- No changes to `ProjectService`, `TaskService`, `UserService`, `SettingsController`'s existing actions, or any domain class.

---

### Task 1: Shared navbar + theme-init templates, wired into the task list

**Files:**
- Create: `grails-app/views/common/_themeInit.gsp`
- Create: `grails-app/views/common/_navbar.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Modify: `grails-app/views/task/index.gsp`

**Interfaces:**
- Consumes: nothing new (existing `_tokens.css` custom properties, existing `createLink`).
- Produces: `g:render template="/common/themeInit"` (no params — reads `localStorage['taskboard-theme']`, sets `data-theme` on `<html>` if present, does nothing else) and `g:render template="/common/navbar" model="[title: String, linkHome: boolean]"` (renders the full `<header class="navbar">`, including the 3 icon links and the theme-toggle button + its click script) — both consumed by Task 2 (`project/list.gsp`), Task 3 (`settings/show.gsp`), and (themeInit only) Task 4 (`login/auth.gsp`).

- [ ] **Step 1: Create `_themeInit.gsp`**

Create `grails-app/views/common/_themeInit.gsp`:

```gsp
<script>
(function () {
    const stored = localStorage.getItem('taskboard-theme');
    if (stored) {
        document.documentElement.setAttribute('data-theme', stored);
    }
})();
</script>
```

- [ ] **Step 2: Create `_navbar.gsp`**

Create `grails-app/views/common/_navbar.gsp`:

```gsp
<header class="navbar">
    <g:if test="${linkHome}">
        <a href="${createLink(controller: 'task')}" class="navbar-title-link"><h1>${title}</h1></a>
    </g:if>
    <g:else>
        <h1>${title}</h1>
    </g:else>
    <div class="navbar-icons">
        <a href="${createLink(controller: 'project')}" class="nav-icon-btn" aria-label="Projekte verwalten">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="M3 7.5A1.5 1.5 0 014.5 6H9l2 2.5h8.5A1.5 1.5 0 0121 10v8a1.5 1.5 0 01-1.5 1.5h-15A1.5 1.5 0 013 18V7.5z"/></svg>
        </a>
        <button type="button" id="theme-toggle" class="nav-icon-btn" aria-label="Darstellung umschalten">
            <svg id="theme-icon-moon" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M21 12.5A8.5 8.5 0 1111.5 3a7 7 0 009.5 9.5z"/></svg>
            <svg id="theme-icon-sun" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" style="display:none"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>
        </button>
        <a href="${createLink(controller: 'settings')}" class="nav-icon-btn" aria-label="Einstellungen">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        </a>
    </div>
</header>

<script>
(function () {
    const btn = document.getElementById('theme-toggle');
    const moonIcon = document.getElementById('theme-icon-moon');
    const sunIcon = document.getElementById('theme-icon-sun');

    function syncIcon() {
        const stored = document.documentElement.getAttribute('data-theme');
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const isDark = stored ? stored === 'dark' : prefersDark;
        moonIcon.style.display = isDark ? 'none' : '';
        sunIcon.style.display = isDark ? '' : 'none';
    }
    syncIcon();

    btn.addEventListener('click', function () {
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const current = document.documentElement.getAttribute('data-theme') || (prefersDark ? 'dark' : 'light');
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('taskboard-theme', next);
        syncIcon();
    });
})();
</script>
```

`_themeInit.gsp` (Step 1) runs in `<head>`, before this script runs in `<body>`, so `document.documentElement.getAttribute('data-theme')` is already correctly set by the time `syncIcon()` first runs.

- [ ] **Step 3: Update `taskboard.css`**

In `grails-app/assets/stylesheets/taskboard.css`, replace this existing rule:

```css
#theme-toggle { background: none; border: none; font-size: 18px; cursor: pointer;
    padding: 0; color: var(--color-text); }
```

with:

```css
.navbar-title-link { text-decoration: none; color: inherit; }
.navbar-icons { display: flex; gap: 4px; align-items: center; }
.nav-icon-btn { background: none; border: none; text-decoration: none; cursor: pointer;
    color: var(--color-text); width: 44px; height: 44px; border-radius: 50%;
    display: inline-flex; align-items: center; justify-content: center; }
```

(The old rule only styled the emoji-text button; the new rules cover the `<a>`-based icon links too, which the old rule never did.)

- [ ] **Step 4: Wire the shared templates into `index.gsp`**

Replace the full contents of `grails-app/views/task/index.gsp` with:

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <link rel="manifest" href="/manifest.json">
    <title>Meine Tasks</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Meine Tasks', linkHome: false]"/>

    <details id="project-manage-section">
        <summary>Projekte verwalten</summary>
        <g:render template="/project/manage" model="[projects: projects, error: null]"/>
    </details>

    <main id="task-list">
        <g:render template="list"
            model="[tasks: tasks, urgencyService: urgencyService, today: today, users: users, projects: projects, selectedProject: selectedProject]"/>
    </main>

    <form id="quick-add" hx-post="${createLink(action: 'quickAdd')}"
          hx-target="#task-list" hx-swap="innerHTML">
        <input type="text" name="title" id="title-input" placeholder="Neuer Task…" required>
        <select name="project">
            <option value="">Kein Projekt</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}">${p.name}</option>
            </g:each>
        </select>
        <button type="button" id="mic-btn" class="fab round-btn" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit" class="round-btn">+</button>
    </form>

    <!-- Session routes keep CSRF protection (see SecurityConfig.groovy); HTMX's
         own POSTs don't carry it automatically, so echo the cookie as a header. -->
    <script>
    document.body.addEventListener('htmx:configRequest', (e) => {
        const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        if (m) e.detail.headers['X-XSRF-TOKEN'] = decodeURIComponent(m[1]);
    });
    </script>
    <asset:javascript src="voice.js"/>
    <asset:javascript src="push.js"/>
    <asset:javascript src="recurrence.js"/>
</body>
</html>
```

Note: the `<details id="project-manage-section">` block is deliberately **kept** in this task (not removed yet) — Task 2 extracts Projekt-Verwaltung to its own page and removes this block then. Until Task 2 lands, the new folder icon in the navbar links to `/project`, which today renders a bare unstyled fragment (no `<html>`/`<head>` wrapper) since `ProjectController.list()` hasn't been changed yet — this is expected and gets fixed in Task 2, not a bug in this task.

- [ ] **Step 5: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, log in as `lars`/`changeme` at `http://localhost:8080`, and confirm:
1. The navbar shows 3 flat icons (folder, moon/sun, gear) instead of the old emoji — inspect them via browser dev tools to confirm they're `<svg>` elements, not text.
2. Clicking the theme icon toggles the whole page's colors and swaps the icon between moon and sun.
3. Reloading the page preserves the manually-chosen theme (via `_themeInit.gsp` now running in `<head>`).
4. The task list, quick-add form, and the (still-present) inline "Projekte verwalten" `<details>` section all still work exactly as before.
5. Clicking the gear icon still goes to `/settings` and works.
6. Clicking the folder icon goes to `/project` and shows the (currently bare/unstyled) project-management fragment — confirm it's not a 404, just unstyled; this is expected until Task 2.

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, TRUE merged total still 116 tests, 0 failures (this task is GSP/CSS/JS only, no Groovy changed, so it's a pure regression check).

- [ ] **Step 7: Commit**

```bash
git add grails-app/views/common/_themeInit.gsp grails-app/views/common/_navbar.gsp grails-app/assets/stylesheets/taskboard.css grails-app/views/task/index.gsp
git commit -m "feat: add shared navbar/theme-init templates with flat SVG icons"
```

---

### Task 2: Extract Projekt-Verwaltung into its own page

**Files:**
- Create: `grails-app/views/project/list.gsp`
- Modify: `grails-app/controllers/taskboard/ProjectController.groovy`
- Modify: `grails-app/views/task/index.gsp`

**Interfaces:**
- Consumes: `g:render template="/common/themeInit"` and `g:render template="/common/navbar" model="[title: String, linkHome: boolean]"` (Task 1).
- Produces: `GET /project` (via `defaultAction`) renders the full Projekt-Verwaltung page — consumed by the navbar's folder icon link (already wired in Task 1, now resolves to a real styled page instead of a bare fragment).

- [ ] **Step 1: Create `project/list.gsp`**

Create `grails-app/views/project/list.gsp`:

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Projekte</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Projekte', linkHome: true]"/>

    <main class="settings-page">
        <g:render template="manage" model="[projects: projects, error: error]"/>
    </main>
</body>
</html>
```

(Reuses the existing `.settings-page` class for the body's padding/max-width — it's a generic "simple content page" wrapper, not settings-specific despite the name; matches the pattern the design-system spec already established for non-task-list pages.)

- [ ] **Step 2: Update `ProjectController.groovy`**

Replace the full contents of `grails-app/controllers/taskboard/ProjectController.groovy` with:

```groovy
package taskboard

/**
 * Project management: create/edit/delete, reachable from its own page
 * (grails-app/views/project/list.gsp, linked from the navbar's folder icon)
 * as of the design-system rollout -- previously an inline <details> widget
 * on the task list page. No @Secured annotation -- relies on SecurityConfig's
 * anyRequest().authenticated(), same as TaskController; /project/** is not
 * in the permitAll matcher list.
 */
class ProjectController {

    static defaultAction = 'list'

    ProjectService projectService

    /** Full page (GET /project) -- unlike create/update/delete below, this is
     *  a real page load, not an HTMX fragment swap, so it renders the 'list'
     *  view (which wraps the 'manage' fragment in a full HTML document with
     *  the shared navbar) rather than the bare template. */
    def list() {
        render view: 'list', model: [projects: Project.list(), error: null]
    }

    def create() {
        def result = projectService.create(params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def update(Long id) {
        def result = projectService.update(id, params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def delete(Long id) {
        projectService.delete(id)
        render template: 'manage', model: [projects: Project.list(), error: null]
    }
}
```

(`create`/`update`/`delete` are unchanged from before — they still render just the `manage` fragment for HTMX to swap into `#project-manage`, which now lives inside `project/list.gsp` instead of inside `task/index.gsp`'s `<details>`.)

- [ ] **Step 3: Remove the inline project-management section from `index.gsp`**

In `grails-app/views/task/index.gsp`, remove this block entirely (it directly follows the `g:render template="/common/navbar"` line added in Task 1):

```gsp
    <details id="project-manage-section">
        <summary>Projekte verwalten</summary>
        <g:render template="/project/manage" model="[projects: projects, error: null]"/>
    </details>
```

So the file goes from:

```gsp
    <g:render template="/common/navbar" model="[title: 'Meine Tasks', linkHome: false]"/>

    <details id="project-manage-section">
        <summary>Projekte verwalten</summary>
        <g:render template="/project/manage" model="[projects: projects, error: null]"/>
    </details>

    <main id="task-list">
```

to:

```gsp
    <g:render template="/common/navbar" model="[title: 'Meine Tasks', linkHome: false]"/>

    <main id="task-list">
```

- [ ] **Step 4: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, log in, then:
1. Confirm the task list page no longer shows the "Projekte verwalten" collapsible section.
2. Click the folder icon in the navbar — confirm it now navigates to a properly styled `/project` page (navbar, centered content, not a bare unstyled fragment).
3. On that page: create a new project, edit an existing one, delete one — confirm all three still work exactly as before (HTMX swaps the list in place, no full page reload).
4. Click the page title ("Projekte") — confirm it links back to the task list (`linkHome: true` in effect).
5. Confirm the gear and theme icons on this new page also work.

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, TRUE merged total still 116 tests, 0 failures. Pay particular attention to `ProjectControllerIntegrationSpec` and `ProjectFlowIntegrationSpec` (existing tests that exercise `/project/create`, `/project/update`, `/project/delete`, and possibly `/project/list`) — if any of them assert on the exact response body of `list()` expecting the bare fragment instead of the full page, they need to keep passing; read them first if the run fails to see what they actually assert.

- [ ] **Step 6: Commit**

```bash
git add grails-app/views/project/list.gsp grails-app/controllers/taskboard/ProjectController.groovy grails-app/views/task/index.gsp
git commit -m "feat: extract Projekt-Verwaltung into its own page"
```

---

### Task 3: Settings page polish

**Files:**
- Modify: `grails-app/views/settings/show.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`

**Interfaces:**
- Consumes: `g:render template="/common/themeInit"` and `g:render template="/common/navbar" model="[title: String, linkHome: boolean]"` (Task 1).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Update `settings/show.gsp`**

Replace the full contents of `grails-app/views/settings/show.gsp` with:

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Einstellungen</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Einstellungen', linkHome: true]"/>

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

- [ ] **Step 2: Polish `.settings-page`/`.token-display` in `taskboard.css`**

In `grails-app/assets/stylesheets/taskboard.css`, replace:

```css
.settings-page { padding: 12px; }
.token-display { font-family: monospace; font-size: 1.1em; padding: 8px;
    width: 100%; max-width: 320px; box-sizing: border-box; margin-bottom: 12px; }
```

with:

```css
.settings-page { padding: 16px; max-width: 480px; }
.settings-page p { margin: 0 0 8px; }
.token-display { font-family: monospace; font-size: 1.1em; padding: 8px;
    width: 100%; max-width: 320px; box-sizing: border-box; margin-bottom: 12px;
    border-radius: 8px; border: 1px solid var(--color-pill-bg);
    background: var(--color-surface); color: var(--color-text); }
```

- [ ] **Step 3: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, log in, click the gear icon:
1. Confirm the page now shows the shared navbar (3 icons, no more standalone "← Zurück" text link).
2. Confirm the page title "Einstellungen" links back to the task list.
3. Confirm the token display box now has a visible rounded border matching other inputs in the app.
4. Confirm "Token neu generieren" still works (confirm dialog, then a new token appears).
5. Toggle dark mode from this page — confirm it works here too (previously only worked from the task list).

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, TRUE merged total still 116 tests, 0 failures. Pay particular attention to `SettingsControllerIntegrationSpec` if it asserts on the exact HTML of `show.gsp`.

- [ ] **Step 5: Commit**

```bash
git add grails-app/views/settings/show.gsp grails-app/assets/stylesheets/taskboard.css
git commit -m "feat: polish Settings page and switch to the shared navbar"
```

---

### Task 4: Login page redesign + error feedback

**Files:**
- Modify: `grails-app/views/login/auth.gsp`
- Modify: `grails-app/controllers/taskboard/LoginController.groovy`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Test: `src/integration-test/groovy/taskboard/LoginErrorIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `g:render template="/common/themeInit"` (Task 1) — no navbar on this page.
- Produces: nothing consumed by other tasks — this is the last task in this plan.

- [ ] **Step 1: Write the failing integration test**

Create `src/integration-test/groovy/taskboard/LoginErrorIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Confirms the login page shows a German error message after a failed login
 * attempt (Spring Security's default form-login failure handler redirects to
 * loginPage + "?error" -- see SecurityConfig, no explicit failureUrl is set),
 * and that a fresh page load without ?error shows no such message.
 */
@Integration
class LoginErrorIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    void "a bad login redirects to the login page with an error param, which then shows a German error message"() {
        given: "an initial GET to resolve the CSRF cookie"
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        initial.headerFields.get('Set-Cookie')?.each { String raw ->
            def pair = raw.split(';')[0].split('=', 2)
            if (pair.length == 2) cookies[pair[0]] = pair[1]
        }

        when: "posting a bad password"
        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookies.collect { k, v -> "${k}=${v}" }.join('; '))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=definitely-wrong&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }

        then: "it redirects back to the login page with an error param"
        login.responseCode == 302
        login.getHeaderField("Location")?.contains("/login/auth") &&
            login.getHeaderField("Location")?.contains("error")

        when: "following that redirect"
        def errorPage = new URL(login.getHeaderField("Location")).openConnection()
        String errorBody = errorPage.inputStream.text

        then: "the error message is shown"
        errorBody.contains("Benutzername oder Passwort falsch")

        when: "loading the login page fresh, with no error param"
        def freshPage = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        String freshBody = freshPage.inputStream.text

        then: "no error message is shown"
        !freshBody.contains("Benutzername oder Passwort falsch")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.LoginErrorIntegrationSpec"`
Expected: FAILURE — `errorBody.contains("Benutzername oder Passwort falsch")` is false, since `auth.gsp` doesn't render any error message yet.

- [ ] **Step 3: Update `LoginController.groovy`**

Replace the full contents of `grails-app/controllers/taskboard/LoginController.groovy` with:

```groovy
package taskboard

class LoginController {

    /** Spring Security's default form-login failure handler redirects here
     *  with ?error on bad credentials (see SecurityConfig.groovy -- no
     *  explicit failureUrl is configured, so loginPage + "?error" is Spring
     *  Security's own default). loginFailed drives the error message in
     *  auth.gsp. */
    def auth() {
        [loginFailed: params.error != null]
    }

    def denied() {}
}
```

- [ ] **Step 4: Rewrite `login/auth.gsp`**

Replace the full contents of `grails-app/views/login/auth.gsp` with:

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>Anmelden</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body class="login-page">
    <div class="login-card">
        <div class="login-badge">✓</div>
        <h1>Family Taskboard</h1>
        <g:if test="${loginFailed}">
            <p class="login-error">Benutzername oder Passwort falsch</p>
        </g:if>
        <form action="/login" method="post">
            <input type="text" name="username" placeholder="Benutzername" required>
            <input type="password" name="password" placeholder="Passwort" required>
            <!-- Accessing _csrf here forces Spring Security to resolve+persist the
                 deferred CSRF token (writing the XSRF-TOKEN cookie); without this,
                 CookieCsrfTokenRepository never issues a cookie on a plain GET and
                 the subsequent POST /login is rejected as a CSRF failure. -->
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <button type="submit" class="login-submit">Anmelden</button>
        </form>
    </div>
</body>
</html>
```

- [ ] **Step 5: Add login styles to `taskboard.css`**

In `grails-app/assets/stylesheets/taskboard.css`, find the existing shared input rule:

```css
input[type="text"], input[type="number"], select {
```

Replace it (adding `input[type="password"]` to the selector list) with:

```css
input[type="text"], input[type="number"], input[type="password"], select {
```

Then, at the end of the file, append:

```css
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.login-card { background: var(--color-surface); border-radius: 12px;
    box-shadow: var(--shadow-card); padding: 40px 32px; width: 300px; box-sizing: border-box; }
.login-badge { width: 44px; height: 44px; border-radius: 50%; background: var(--color-text);
    color: var(--color-bg); display: flex; align-items: center; justify-content: center;
    font-size: 20px; margin: 0 auto 16px; }
.login-card h1 { margin: 0 0 24px; font-size: 18px; font-weight: 700; color: var(--color-text);
    text-align: center; }
.login-error { color: var(--color-urgency-red); font-size: 13px; text-align: center; margin: 0 0 16px; }
.login-card form { display: flex; flex-direction: column; gap: 12px; }
.login-submit { border-radius: 8px; border: none; background: var(--color-text);
    color: var(--color-bg); padding: 11px; font-family: 'Inter', system-ui, sans-serif;
    margin-top: 6px; cursor: pointer; }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.LoginErrorIntegrationSpec"`
Expected: PASS.

- [ ] **Step 7: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, in a browser at `http://localhost:8080`:
1. Confirm the login page now shows a centered white card with a ✓ badge, on the tokenized background, with rounded inputs and a dark rounded button — not the old raw browser-default form.
2. Try logging in with a wrong password — confirm "Benutzername oder Passwort falsch" appears in red above the form.
3. Log in successfully with `lars`/`changeme` — confirm it still works and lands on the task list.
4. Toggle dark mode from the task list, log out, reload the login page — confirm the login page now renders in dark mode too (this is the cross-page dark-mode-sync bug this whole plan set out to fix, now verified end-to-end).

- [ ] **Step 8: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL. TRUE merged total should now be 117 tests, 0 failures (116 baseline + 1 new `LoginErrorIntegrationSpec` test). Verify via `build/reports/tests/index.html`'s merged counters directly.

- [ ] **Step 9: Commit**

```bash
git add grails-app/views/login/auth.gsp grails-app/controllers/taskboard/LoginController.groovy grails-app/assets/stylesheets/taskboard.css src/integration-test/groovy/taskboard/LoginErrorIntegrationSpec.groovy
git commit -m "feat: redesign login page with tokenized styling and error feedback"
```

---

## Self-Review Notes

- **Spec coverage:** §1 (shared templates) → Task 1. §2 (navbar icons) → Task 1's exact SVG paths. §3 (login page) → Task 4. §4 (settings polish + home-link resolution) → Task 3 (polish) + Task 1 (`_navbar.gsp`'s `linkHome` logic, produced once, consumed by both). §5 (Projekt as its own page) → Task 2. §6 (task-list changes) → split across Task 1 (navbar swap) and Task 2 (removing the inline details block), sequenced so the folder icon always eventually resolves to a real page and no task leaves the app in a permanently broken state.
- **Placeholder scan:** none found — every step has literal, complete code.
- **Type/name consistency:** `_themeInit.gsp` (no params) and `_navbar.gsp` (`title`, `linkHome`) are defined once in Task 1 and referenced identically (same template path, same param names) in Tasks 2-4. `.nav-icon-btn`/`.navbar-icons`/`.navbar-title-link` (Task 1), `.login-*` classes (Task 4), and the `.settings-page`/`.token-display` tweaks (Task 3) don't collide with any existing class names (checked against the current `taskboard.css` contents).
- **Transitional-state check:** Task 1 explicitly calls out that the folder icon will link to an unstyled bare fragment until Task 2 lands — flagged in Step 4's note and Step 5's manual-verification step, so neither the implementer nor reviewer mistakes this for a bug within Task 1's own scope.
