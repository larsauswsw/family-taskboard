# Login, Settings & Projekt-Verwaltung Redesign — Design Spec

**Date:** 2026-07-09
**Status:** Approved by user, ready for planning

## Problem

The design-system work (self-hosted fonts, color tokens, dark mode, rounded cards) only touched the task-list page. Three other surfaces were explicitly deferred at the time:

- **Login** (`login/auth.gsp`): zero styling at all — no stylesheet linked, raw browser-default form. Failed logins also show no feedback (`?error` param is never read).
- **Settings** (`settings/show.gsp`): already loads `taskboard.css` and inherits most tokens, just needs polish.
- **Projekt-Verwaltung** (`project/_manage.gsp`): currently an inline `<details>` widget embedded in the task-list page, not its own page.

Additionally, the navbar's existing icons (⚙️ settings, 🌙/☀️ theme toggle) are colorful emoji that render inconsistently across platforms and don't visually match the flat, tokenized aesthetic established elsewhere.

## Goal

Bring all three surfaces to the same visual standard as the task list, reusing the existing tokens with no new colors/fonts/decisions. Extract Projekt-Verwaltung into its own page. Replace emoji navbar icons with flat, single-color SVG icons that follow theme via `currentColor`. Fix a real gap found along the way: the manual dark-mode preference only applies on the task-list page today; it needs to apply everywhere.

## Scope

In scope: `login/auth.gsp` + `LoginController`, `settings/show.gsp`, a new `project/list.gsp` + `ProjectController` change, `task/index.gsp` (removing the inline project widget, switching to the shared navbar), two new shared templates, and `taskboard.css` additions for all of the above.

Out of scope: any further layout/content changes to the task list itself (already done), any new domain logic, any change to how `_manage.gsp`'s create/update/delete HTMX flow works internally (only where it's rendered from changes).

## Design

### 1. Shared templates

Two new templates under a new `grails-app/views/common/` directory, used by multiple controllers (GSP's `g:render template="/common/..."` syntax supports cross-controller references):

- **`_themeInit.gsp`**: just the "read `taskboard-theme` from `localStorage`, set `data-theme` on `<html>` if present" logic — no toggle button, no click handler. Included in the `<head>` of **all four** pages (Login, Settings, the new Projekt page, and the existing task list), so a previously-chosen dark-mode preference is honored everywhere, not just on the task list where it was originally set.
- **`_navbar.gsp`**: the full `<header class="navbar">` — page `<h1>` (passed in as a `title` template parameter), plus all 3 flat-icon links (folder → Projekt-Verwaltung, theme toggle, gear → Settings) and the toggle button's click-handler script. Included via `g:render template="/common/navbar" model="[title: '...', linkHome: true]"` on Settings and the new Projekt page, and `model="[title: 'Meine Tasks', linkHome: false]"` on the task list itself. When `linkHome` is true, the `<h1>` is wrapped in a link to `createLink(controller: 'task')` (see §4); when false, it's plain text (already on the task list, linking to itself would be pointless). **All 3 icons always show on all 3 pages**, including a link to the page you're already on (a harmless self-navigation, not a no-op — simpler and more consistent than conditionally hiding the current page's icon).

Login has no navbar (confirmed: no toggle button there either) — it only gets `_themeInit.gsp` in its `<head>`.

### 2. Navbar icons

Three flat, single-color SVG icons (viewBox `0 0 24 24`, `stroke="currentColor"`, `fill="none"`, no `fill` colors) replace the current emoji. Since they use `currentColor`, they automatically follow `var(--color-text)` and therefore adapt to dark mode with no extra work:

**Folder** (Projekt-Verwaltung link), `stroke-width="1.6"`, `stroke-linejoin="round"`:
```
<path d="M3 7.5A1.5 1.5 0 014.5 6H9l2 2.5h8.5A1.5 1.5 0 0121 10v8a1.5 1.5 0 01-1.5 1.5h-15A1.5 1.5 0 013 18V7.5z"/>
```

**Moon** (theme toggle, shown when currently in light mode — click switches to dark), `stroke-width="1.6"`:
```
<path d="M21 12.5A8.5 8.5 0 1111.5 3a7 7 0 009.5 9.5z"/>
```

**Sun** (theme toggle, shown when currently in dark mode — click switches to light), `stroke-width="1.6"`, `stroke-linecap="round"`:
```
<circle cx="12" cy="12" r="5"/>
<path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
```

**Gear** (Settings link), `stroke-width="1.5"`, `stroke-linecap="round"`, `stroke-linejoin="round"`:
```
<circle cx="12" cy="12" r="3"/>
<path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>
```

Each icon sits in a `.nav-icon-btn` wrapper (44×44px, `background: none`, `border: none`, `color: var(--color-text)`, flex-centered, `cursor: pointer`) — this replaces the old `#theme-toggle` CSS rule and the plain `<a>`/emoji markup for the settings link.

The theme toggle keeps its existing swap behavior (moon ↔ sun icon, same `localStorage` persistence logic), just rendered as SVG instead of emoji text content.

### 3. Login page

Full rewrite of `login/auth.gsp`, matching the approved "Option B" mockup:

- `<body>` background `var(--color-bg)`, flex-centered both axes, `min-height: 100vh`.
- A `.login-card` (`var(--color-surface)` background, `border-radius: 12px`, `box-shadow: var(--shadow-card)`, ~300px wide, generous padding).
- A `.login-badge`: 44px circle, `var(--color-text)` background, white `✓` character, centered above the title — reusing the same checkmark already meaningful elsewhere in the app (the task-card complete button), not a new invented symbol.
- Title `Family Taskboard`, then the username/password inputs (reusing the existing tokenized `input[type="text"]`/`input[type="password"]` rules from `taskboard.css` — note: `input[type="password"]` isn't currently in that selector list and needs to be added) and a `.round-btn`-styled-but-full-width submit button (or a new `.login-submit` rule — full-width, same visual treatment as `button[type="submit"]`).
- Loads `_tokens.css`/`taskboard.css` via `<asset:stylesheet src="taskboard.css"/>` in `<head>` (currently missing entirely) and includes `_themeInit.gsp`.

**Error feedback:** `LoginController.auth()` changes from `def auth() {}` to reading `params.error` and passing `[loginFailed: params.error != null]` as the model. The view shows a `.login-error` message (`Benutzername oder Passwort falsch`, `color: var(--color-urgency-red)`, small text, positioned above the form) when `loginFailed` is true. Spring Security's default form-login failure handler already redirects to `loginPage + "?error"` on bad credentials — no `SecurityConfig` change needed, just reading the param that's already being sent.

### 4. Settings page polish

No structural changes — same token display, same regenerate-token form. Changes:
- Replace the bespoke `<header class="navbar">...</header>` block (which currently has an explicit "← Zurück" text link) with `g:render template="/common/navbar" model="[title: 'Einstellungen', linkHome: true]"`.
- Tighten `.settings-page` spacing/typography to match the token system's type scale (labels, token display box) — no new class names needed beyond what already exists, just value adjustments in `taskboard.css`.

**Getting back to the task list:** the shared navbar's 3 icons (Projekt-Verwaltung / Theme / Settings) don't include a "back to task list" icon, so removing the old "← Zurück" link would otherwise leave Settings and the new Projekt page with no way back except the browser's own back button. `_navbar.gsp` makes the page's `<h1>` title itself a link to the task list (`createLink(controller: 'task')`) on every page except the task list itself (where it's already the current page, so it stays plain text) — a standard "site title = home link" pattern that solves this without adding a 4th icon.

### 5. Projekt-Verwaltung as its own page

- New `grails-app/views/project/list.gsp`: full HTML document, loads `taskboard.css`, includes `_themeInit.gsp` in `<head>` and `g:render template="/common/navbar" model="[title: 'Projekte', linkHome: true]"` for its header, then `g:render template="manage" model="[projects: projects, error: error]"` for the body (the existing fragment, unchanged).
- `ProjectController.list()` changes from `render template: 'manage', model: [...]` to `render view: 'list', model: [...]` (same model shape — `projects`, `error`) — this makes `GET /project/list` (or `/project`, once `defaultAction` is set) serve the full page instead of a bare fragment.
- `create()`, `update()`, `delete()` are **unchanged** — they keep `render template: 'manage', ...` since they're HTMX POSTs that swap the `#project-manage` div's contents in place; they don't need the full page.
- `ProjectController` gains `static defaultAction = 'list'` (matching `SettingsController`'s existing pattern), so `createLink(controller: 'project')` (used by the navbar's folder icon) resolves without an explicit action.
- `task/index.gsp` loses its `<details id="project-manage-section"><summary>Projekte verwalten</summary>...</details>` block entirely — project management now lives only on `/project`.

### 6. Task list (`index.gsp`) changes

- The existing inline `<header class="navbar">` (with the old emoji theme-toggle button and settings link) and the existing inline `<script>` block that handles the toggle's click logic are both removed, replaced by `g:render template="/common/navbar" model="[title: 'Meine Tasks', linkHome: false]"`.
- The existing inline dark-mode-read script (currently duplicated logic at the top of the toggle's script block) is replaced by including `_themeInit.gsp` in `<head>`.
- The inline project-management `<details>` section (§5) is removed.
- Everything else (task list, quick-add form, mic button) is unchanged.

## Files touched

- Modify: `grails-app/views/login/auth.gsp` (full rewrite)
- Modify: `grails-app/controllers/taskboard/LoginController.groovy` (`auth()` reads `params.error`)
- Modify: `grails-app/views/settings/show.gsp` (navbar swap, spacing polish)
- Modify: `grails-app/controllers/taskboard/ProjectController.groovy` (`list()` renders full view, `defaultAction`)
- Create: `grails-app/views/project/list.gsp`
- Modify: `grails-app/views/task/index.gsp` (remove inline navbar/theme-script/project-details, add shared includes)
- Create: `grails-app/views/common/_navbar.gsp`
- Create: `grails-app/views/common/_themeInit.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css` (`.nav-icon-btn`, `.login-card`/`.login-badge`/`.login-error`, `input[type="password"]` added to the existing input selector, settings spacing tweaks)

No Groovy domain/service changes, no new tests beyond a `LoginController` unit test for the `params.error` → model mapping (Spock, no automated test possible for the pure-CSS/layout parts, consistent with prior design-system work).

## Testing

- New: a small `LoginControllerSpec` (or extend an existing one if present) verifying `auth()` sets `loginFailed: true` when `params.error` is present, `false`/absent otherwise.
- Existing `SecurityFilterChainIntegrationSpec`/`TaskControllerSessionFlowIntegrationSpec` must still pass unchanged — login/session behavior itself isn't changing, only the view.
- No automated tests for the pure CSS/GSP layout changes (consistent with the original design-system work) — manual browser verification covers: login card renders correctly with tokens, a wrong password shows the error message, dark-mode preference set on one page persists when navigating to another (the core bug this spec fixes), the new `/project` page's create/update/delete still work via HTMX exactly as before, and the 3 navbar icons render and link correctly on all 3 pages in both light and dark mode.

## Edge cases

- Navigating to `/project` (or clicking the folder icon) while `data-theme` is set in `localStorage`: must render in the correct theme immediately (via `_themeInit.gsp`), not flash light-then-dark.
- A user with JavaScript disabled: `_themeInit.gsp`'s script simply never runs, page falls back to `prefers-color-scheme` only — no worse than today's behavior on the task list.
- Login page's error message must not persist across a *successful* subsequent login attempt on the same tab — since it's driven purely by `params.error` on that one request/render, not stored client-side, this is automatic (a fresh GET without `?error` shows no message).
