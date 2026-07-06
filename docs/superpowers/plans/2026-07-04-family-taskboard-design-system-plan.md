# Design System + Task List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app's ad-hoc, unstyled base look with a consistent "Signalsystem" design (self-hosted Inter/JetBrains Mono typography, light+dark color tokens, rounded cards/pills/buttons) and redesign the task list to group tasks by due-date bucket (Überfällig/Heute/Diese Woche/Später) instead of a flat chain.

**Architecture:** A new `_tokens.css` partial defines all colors as CSS custom properties (light default + `@media (prefers-color-scheme: dark)` override + a `data-theme` attribute override for the manual toggle), included via a `*= require` directive at the top of `taskboard.css`, which is rewritten to use the tokens for every component rule. `UrgencyService` gains a `bucketFor()` method (same deterministic `today`-parameter pattern as the existing `colorFor()`) that `_list.gsp` uses to group the already-sorted task list. `Priority` gains a `germanLabel` field. A small inline script in `index.gsp` handles the manual dark-mode toggle via `localStorage`.

**Tech Stack:** Grails 7.1.1, GORM, Spock (unit), CSS custom properties, self-hosted `.woff2` fonts — no new runtime dependencies.

## Global Constraints

- Fonts are self-hosted under `grails-app/assets/fonts/` — never reference an external font CDN at runtime (the app must work with no internet access once deployed).
- Color tokens (exact hex values), typography scale, and component rules follow the approved design spec verbatim (`docs/superpowers/specs/2026-07-04-family-taskboard-design-system-design.md` §2-§7) — copied into the tasks below, do not invent different values.
- `bucketFor()` boundaries: `daysOut < 0` → `"Überfällig"`, `== 0` → `"Heute"`, `1..6` → `"Diese Woche"`, `>= 7` → `"Später"` (inclusive at both ends of the `1..6` range).
- `UrgencyService.colorFor()` and `effectiveDays()` are NOT changed by this plan — `bucketFor()` is purely additive.
- Scope is the design-system foundation + the task list page only (navbar, filter pills, quick-add form, task cards, recurrence controls on the card). Project-management-page-specific styling, the Settings page, and the Login page are explicitly out of scope — later, separate plans.
- Dark-mode preference is stored in `localStorage` only (no new `User` field, no server round-trip).
- No Spock tests for pure CSS/visual changes — verify those manually in a browser. Spock tests only for `bucketFor()` and `Priority.germanLabel`.

---

### Task 1: Self-hosted fonts + design tokens

**Files:**
- Create: `grails-app/assets/fonts/inter/inter-400.woff2`, `inter-600.woff2`, `inter-700.woff2`
- Create: `grails-app/assets/fonts/jetbrains-mono/jetbrains-mono-400.woff2`
- Create: `grails-app/assets/stylesheets/_tokens.css`
- Modify: `grails-app/assets/stylesheets/taskboard.css` (add `*= require` directive at the very top)

**Interfaces:**
- Consumes: nothing (foundational).
- Produces: CSS custom properties (`--color-bg`, `--color-surface`, `--color-text`, `--color-text-muted`, `--color-pill-bg`, `--color-pill-text`, `--color-pill-active-bg`, `--color-pill-active-text`, `--shadow-card`, `--color-urgency-green/yellow/orange/red/darkred`) and two `@font-face`-registered font families (`'Inter'`, `'JetBrains Mono'`) that every later task's CSS rules reference by name.

- [ ] **Step 1: Download the font files**

Google Fonts serves genuinely open-source font files (Inter and JetBrains Mono are both SIL Open Font License) at predictable URLs discoverable through its public CSS API — this is a one-time download during implementation, not a runtime dependency. The `latin` subset alone covers German (ä/ö/ü/ß are all within `U+0000-00FF`, included in Google's `latin` subset definition for these fonts).

**Important:** Google's CSS API returns `@font-face` blocks for ALL subsets (cyrillic, greek, vietnamese, latin-ext, latin, etc.) regardless of the `&subset=` query parameter — it does not actually filter the response. Each block is preceded by a `/* subsetname */` comment. You must extract the URL from the block specifically commented `/* latin */` (exact match — NOT `/* latin-ext */`, which is a different, larger, non-German-specific block that happens to appear physically closer to the top of the response). A naive "grab the first `url(...)` in the response" will silently pick the wrong subset.

Run:
```bash
mkdir -p grails-app/assets/fonts/inter grails-app/assets/fonts/jetbrains-mono

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

for weight in 400 600 700; do
  css=$(curl -s -A "$UA" "https://fonts.googleapis.com/css?family=Inter:${weight}&subset=latin&display=swap")
  url=$(echo "$css" | awk '/\/\* latin \*\//{f=1} f && /url\(/{print; exit}' | grep -oE "https://[^)]+\.woff2")
  curl -s "$url" -o "grails-app/assets/fonts/inter/inter-${weight}.woff2"
done

css=$(curl -s -A "$UA" "https://fonts.googleapis.com/css?family=JetBrains+Mono:400&subset=latin&display=swap")
url=$(echo "$css" | awk '/\/\* latin \*\//{f=1} f && /url\(/{print; exit}' | grep -oE "https://[^)]+\.woff2")
curl -s "$url" -o "grails-app/assets/fonts/jetbrains-mono/jetbrains-mono-400.woff2"
```

- [ ] **Step 2: Verify the downloaded files are genuine font data, not error pages**

Run: `file grails-app/assets/fonts/inter/*.woff2 grails-app/assets/fonts/jetbrains-mono/*.woff2`
Expected: all four lines report `Web Open Font Format` (or similar binary font format description) — NOT "ASCII text" or "HTML document", which would mean the download failed (e.g. Google blocked the request or returned an error page instead of a font). If any file is wrong, re-run Step 1's `curl` for that specific weight and inspect the intermediate `$css`/`$url` variables by echoing them.

- [ ] **Step 3: Create `_tokens.css`**

Create `grails-app/assets/stylesheets/_tokens.css`:

```css
@font-face {
  font-family: 'Inter';
  src: url('../fonts/inter/inter-400.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'Inter';
  src: url('../fonts/inter/inter-600.woff2') format('woff2');
  font-weight: 600;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'Inter';
  src: url('../fonts/inter/inter-700.woff2') format('woff2');
  font-weight: 700;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'JetBrains Mono';
  src: url('../fonts/jetbrains-mono/jetbrains-mono-400.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

:root {
  --color-bg: #F7F7F5;
  --color-surface: #FFFFFF;
  --color-text: #1A1A1A;
  --color-text-muted: #8A8A85;
  --color-pill-bg: #EAEAE7;
  --color-pill-text: #5A5A55;
  --color-pill-active-bg: #1A1A1A;
  --color-pill-active-text: #FFFFFF;
  --shadow-card: 0 1px 3px rgba(0,0,0,.05);

  --color-urgency-green: #3B8C3B;
  --color-urgency-yellow: #C79A1E;
  --color-urgency-orange: #D8752E;
  --color-urgency-red: #D9422E;
  --color-urgency-darkred: #7A1F1F;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg: #16181B;
    --color-surface: #212327;
    --color-text: #F0F0EE;
    --color-text-muted: #9A9A95;
    --color-pill-bg: #2A2C30;
    --color-pill-text: #C5C5C0;
    --color-pill-active-bg: #F0F0EE;
    --color-pill-active-text: #16181B;
    --shadow-card: 0 1px 3px rgba(0,0,0,.3);

    --color-urgency-green: #4CAF50;
    --color-urgency-yellow: #D9A62E;
    --color-urgency-orange: #E2864A;
    --color-urgency-red: #E5564A;
    --color-urgency-darkred: #C23B3B;
  }
}

:root[data-theme="dark"] {
  --color-bg: #16181B;
  --color-surface: #212327;
  --color-text: #F0F0EE;
  --color-text-muted: #9A9A95;
  --color-pill-bg: #2A2C30;
  --color-pill-text: #C5C5C0;
  --color-pill-active-bg: #F0F0EE;
  --color-pill-active-text: #16181B;
  --shadow-card: 0 1px 3px rgba(0,0,0,.3);

  --color-urgency-green: #4CAF50;
  --color-urgency-yellow: #D9A62E;
  --color-urgency-orange: #E2864A;
  --color-urgency-red: #E5564A;
  --color-urgency-darkred: #C23B3B;
}

:root[data-theme="light"] {
  --color-bg: #F7F7F5;
  --color-surface: #FFFFFF;
  --color-text: #1A1A1A;
  --color-text-muted: #8A8A85;
  --color-pill-bg: #EAEAE7;
  --color-pill-text: #5A5A55;
  --color-pill-active-bg: #1A1A1A;
  --color-pill-active-text: #FFFFFF;
  --shadow-card: 0 1px 3px rgba(0,0,0,.05);

  --color-urgency-green: #3B8C3B;
  --color-urgency-yellow: #C79A1E;
  --color-urgency-orange: #D8752E;
  --color-urgency-red: #D9422E;
  --color-urgency-darkred: #7A1F1F;
}
```

- [ ] **Step 4: Wire `_tokens.css` into `taskboard.css`**

In `grails-app/assets/stylesheets/taskboard.css`, add this as the very first line (before any existing rule):

```css
/*
*= require _tokens
*/
```

- [ ] **Step 5: Commit**

```bash
git add grails-app/assets/fonts grails-app/assets/stylesheets/_tokens.css grails-app/assets/stylesheets/taskboard.css
git commit -m "feat: add self-hosted Inter/JetBrains Mono fonts and design tokens"
```

- [ ] **Step 6: Manual verification (do this once Task 4 has also updated `taskboard.css`'s `body` rule to use `font-family: 'Inter'` — come back to this step after Task 4, or check now that the `@font-face` rules at least parse without a build error)**

Run: `./gradlew assetCompile` (or start the app via `bootRun` once in this session) and confirm the build produces no asset-pipeline errors referencing `_tokens.css` or the font files. Full visual font verification (fonts actually rendering) happens in Task 4's manual check, once `body` actually applies `font-family: 'Inter'`.

---

### Task 2: `Priority.germanLabel`

**Files:**
- Modify: `src/main/groovy/taskboard/Priority.groovy`
- Modify: `grails-app/views/task/_card.gsp:24`
- Test: `src/test/groovy/taskboard/PrioritySpec.groovy`

**Interfaces:**
- Consumes: nothing new.
- Produces: `Priority.germanLabel` (String field on the enum) — `_card.gsp` and any later view work reference this instead of the raw enum name.

- [ ] **Step 1: Write the failing test**

Create `src/test/groovy/taskboard/PrioritySpec.groovy`:

```groovy
package taskboard

import spock.lang.Specification
import spock.lang.Unroll

class PrioritySpec extends Specification {

    @Unroll
    void "#priority has German label #expected"() {
        expect:
        priority.germanLabel == expected

        where:
        priority          | expected
        Priority.LOW      | "Niedrig"
        Priority.MEDIUM   | "Mittel"
        Priority.HIGH     | "Hoch"
        Priority.CRITICAL | "Kritisch"
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "taskboard.PrioritySpec"`
Expected: compilation FAILURE — `Priority.germanLabel` doesn't exist yet.

- [ ] **Step 3: Add `germanLabel` to `Priority.groovy`**

Replace the full contents of `src/main/groovy/taskboard/Priority.groovy` with:

```groovy
package taskboard

/**
 * multiplier scales down "days until due" in UrgencyService, so a higher-priority
 * task reaches each color band sooner than a lower-priority one with the same due date.
 * germanLabel is the display string shown on task cards (never the raw enum name).
 */
enum Priority {
    LOW(1.0d, "Niedrig"), MEDIUM(1.2d, "Mittel"),
    HIGH(1.5d, "Hoch"), CRITICAL(2.0d, "Kritisch")
    final double multiplier
    final String germanLabel
    Priority(double m, String label) { this.multiplier = m; this.germanLabel = label }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "taskboard.PrioritySpec"`
Expected: PASS (4 tests).

- [ ] **Step 5: Update `_card.gsp` to show the German label**

In `grails-app/views/task/_card.gsp`, replace:

```gsp
        <span class="badge">${task.priority}</span>
```

with:

```gsp
        <span class="badge">${task.priority.germanLabel}</span>
```

- [ ] **Step 6: Run the full unit suite to confirm nothing broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all unit specs pass, including the new `PrioritySpec`.

- [ ] **Step 7: Commit**

```bash
git add src/main/groovy/taskboard/Priority.groovy grails-app/views/task/_card.gsp src/test/groovy/taskboard/PrioritySpec.groovy
git commit -m "feat: show German priority labels instead of raw enum names"
```

---

### Task 3: `UrgencyService.bucketFor()`

**Files:**
- Modify: `grails-app/services/taskboard/UrgencyService.groovy`
- Test: `src/test/groovy/taskboard/UrgencyServiceSpec.groovy`

**Interfaces:**
- Consumes: nothing new (uses the existing `Task`/`LocalDate` types already used by `colorFor()`).
- Produces: `UrgencyService.bucketFor(Task task, LocalDate today) -> String`, returning exactly one of `"Überfällig"`, `"Heute"`, `"Diese Woche"`, `"Später"`.

- [ ] **Step 1: Write the failing tests**

Append this test method to `src/test/groovy/taskboard/UrgencyServiceSpec.groovy`, inside the existing `UrgencyServiceSpec` class (reuse the file's existing private `task(int daysOut, Priority p)` helper — it already exists in this file for the `colorFor()` tests):

```groovy
    @Unroll
    void "#daysOut days out gets bucket #expected"() {
        given:
        def today = LocalDate.of(2026,1,1)

        expect:
        service.bucketFor(task(daysOut, Priority.LOW), today) == expected

        where:
        daysOut | expected
        -1      | "Überfällig"
        0       | "Heute"
        1       | "Diese Woche"
        6       | "Diese Woche"
        7       | "Später"
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "taskboard.UrgencyServiceSpec"`
Expected: compilation FAILURE — `bucketFor` doesn't exist yet on `UrgencyService`.

- [ ] **Step 3: Add `bucketFor()` to `UrgencyService.groovy`**

In `grails-app/services/taskboard/UrgencyService.groovy`, add this method right after the existing `colorFor()` method (before the closing `}` of the class):

```groovy

    /** Groups a task by how far out its due date is, for the task-list's
     *  section headers -- purely additive to colorFor()/effectiveDays(),
     *  does not affect the color logic. Boundaries: <0 days overdue, 0 days
     *  is "today", 1-6 days is "this week" (inclusive), 7+ is "later". */
    String bucketFor(Task task, LocalDate today) {
        long daysOut = ChronoUnit.DAYS.between(today, task.dueDate)
        if (daysOut < 0) return "Überfällig"
        if (daysOut == 0) return "Heute"
        if (daysOut <= 6) return "Diese Woche"
        return "Später"
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "taskboard.UrgencyServiceSpec"`
Expected: PASS (all existing `colorFor`/`effectiveDays` tests plus the 5 new `bucketFor` cases).

- [ ] **Step 5: Run the full unit suite to confirm nothing broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add grails-app/services/taskboard/UrgencyService.groovy src/test/groovy/taskboard/UrgencyServiceSpec.groovy
git commit -m "feat: add UrgencyService.bucketFor for due-date grouping"
```

---

### Task 4: Grouped task list + component styling

**Files:**
- Modify: `grails-app/views/task/_list.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Modify: `grails-app/views/task/index.gsp` (button classes only, no new elements yet — the dark-mode toggle button itself is Task 5)

**Interfaces:**
- Consumes: `UrgencyService.bucketFor(Task, LocalDate) -> String` (Task 3), `_tokens.css`'s CSS custom properties (Task 1), `Priority.germanLabel` (Task 2, already wired into `_card.gsp`).
- Produces: a `.bucket-label` CSS class and a `.round-btn` CSS class that Task 5 (and any later work) can reuse.

- [ ] **Step 1: Update `_list.gsp` to group tasks by bucket**

Replace the full contents of `grails-app/views/task/_list.gsp` with:

```gsp
<div id="project-filter">
    <a href="#" class="project-pill ${selectedProject == null ? 'active' : ''}"
       hx-get="${createLink(action: 'list')}" hx-target="#task-list" hx-swap="innerHTML">Alle</a>
    <a href="#" class="project-pill ${selectedProject == 'none' ? 'active' : ''}"
       hx-get="${createLink(action: 'list', params: [project: 'none'])}"
       hx-target="#task-list" hx-swap="innerHTML">Kein Projekt</a>
    <g:each in="${projects}" var="p">
        <a href="#" class="project-pill ${selectedProject == p.id.toString() ? 'active' : ''}"
           style="background-color: ${p.color};"
           hx-get="${createLink(action: 'list', params: [project: p.id])}"
           hx-target="#task-list" hx-swap="innerHTML">${p.name}</a>
    </g:each>
</div>
<g:each in="${tasks.groupBy { urgencyService.bucketFor(it, today) }}" var="bucketEntry">
    <div class="bucket-label">${bucketEntry.key}</div>
    <g:each in="${bucketEntry.value}" var="task">
        <g:render template="card"
            model="[task: task, color: urgencyService.colorFor(task, today), users: users, projects: projects]"/>
    </g:each>
</g:each>
```

`tasks.groupBy { ... }` returns a `LinkedHashMap` that preserves first-encounter order from the source list; since `tasks` is already sorted by `dueDate` ascending (every `TaskService` query already does this), overdue tasks are always encountered first, then today's, then this week's, then later ones — so the buckets come out in exactly the right visual order (`Überfällig` → `Heute` → `Diese Woche` → `Später`) with no extra sorting code needed. An empty bucket simply never appears as a map key, so there's no empty-section-header case to guard against.

- [ ] **Step 2: Rewrite `taskboard.css`'s component rules**

Replace the full contents of `grails-app/assets/stylesheets/taskboard.css` with:

```css
/*
*= require _tokens
*/

body {
    margin: 0;
    font-family: 'Inter', system-ui, sans-serif;
    background: var(--color-bg);
    color: var(--color-text);
}

.navbar { display: flex; justify-content: space-between; align-items: center;
    padding: 16px; gap: 12px; }
.navbar h1 { font-size: 24px; font-weight: 700; margin: 0; }

.bucket-label { font-size: 12px; font-weight: 700; letter-spacing: 1.5px;
    text-transform: uppercase; color: var(--color-text-muted); margin: 16px 8px 8px; }

.task-card { border-left: 5px solid; padding: 12px 16px; margin: 8px;
    border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-card); }
.task-card.green   { border-left-color: var(--color-urgency-green); }
.task-card.yellow  { border-left-color: var(--color-urgency-yellow); }
.task-card.orange  { border-left-color: var(--color-urgency-orange); }
.task-card.red     { border-left-color: var(--color-urgency-red); }
.task-card.darkred { border-left-color: var(--color-urgency-darkred); }
.task-title { font-size: 15px; font-weight: 600; color: var(--color-text); margin: 0; }
.task-meta { font-family: 'JetBrains Mono', monospace; font-size: 11px;
    color: var(--color-text-muted); display: flex; align-items: center; gap: 8px; margin-top: 6px; }

.round-btn { border-radius: 50%; border: none; background: var(--color-text);
    color: var(--color-bg); font-size: 20px; width: 44px; height: 44px;
    display: inline-flex; align-items: center; justify-content: center; }
.fab { position: fixed; bottom: 16px; right: 16px; width: 56px; height: 56px; font-size: 22px; }

.badge { font-family: 'Inter', system-ui, sans-serif; background: var(--color-pill-bg);
    color: var(--color-pill-text); border-radius: 6px; padding: 2px 6px; font-size: 11px; }

.project-chip { display: inline-block; padding: 2px 10px; border-radius: 999px;
    font-size: 0.75em; color: #fff; margin-bottom: 4px; }
.project-pill { display: inline-block; padding: 4px 12px; margin: 2px;
    border-radius: 999px; background: var(--color-pill-bg); text-decoration: none;
    color: var(--color-pill-text); font-size: 0.85em; }
.project-pill.active { background: var(--color-pill-active-bg);
    color: var(--color-pill-active-text); font-weight: bold; }

input[type="text"], input[type="number"], select {
    border-radius: 8px; border: 1px solid var(--color-pill-bg);
    background: var(--color-surface); color: var(--color-text); padding: 8px 12px;
    font-family: 'Inter', system-ui, sans-serif;
}
button[type="submit"] {
    border-radius: 8px; border: none; background: var(--color-text);
    color: var(--color-bg); padding: 8px 16px; font-family: 'Inter', system-ui, sans-serif;
}

.recurrence-badge { margin-left: 4px; }
.recurrence-details { margin-top: 6px; font-size: 0.85em; }
.recurrence-details summary { cursor: pointer; }
.recurrence-form span { margin-right: 8px; }
.settings-page { padding: 12px; }
.token-display { font-family: monospace; font-size: 1.1em; padding: 8px;
    width: 100%; max-width: 320px; box-sizing: border-box; margin-bottom: 12px; }
```

Note: `button[type="submit"]` is a broad selector that also affects the "Speichern"/"Anlegen"/"Löschen" buttons inside the project-management section (rendered on the same task-list page) and the recurrence form's "Übernehmen"/"Serie beenden" buttons — this is intentional and consistent (they're on this page, they get the same base button treatment), not a redesign of the Project-Verwaltung page itself (that page's own dedicated layout work is a separate, later plan). The quick-add "+" button and the mic button get `.round-btn` instead (Step 3) since the design calls for them to be circular, not the rectangular `button[type=submit]` style.

- [ ] **Step 3: Add the `round-btn` class to the quick-add submit button and mic button**

In `grails-app/views/task/index.gsp`, replace:

```gsp
        <button type="button" id="mic-btn" class="fab" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit">+</button>
```

with:

```gsp
        <button type="button" id="mic-btn" class="fab round-btn" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit" class="round-btn">+</button>
```

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL — all specs pass (this task touches no Groovy logic, only GSP/CSS, so this is a regression check, not new coverage).

- [ ] **Step 5: Commit**

```bash
git add grails-app/views/task/_list.gsp grails-app/assets/stylesheets/taskboard.css grails-app/views/task/index.gsp
git commit -m "feat: group task list by due-date bucket and apply the new design tokens"
```

- [ ] **Step 6: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, then in a browser at `http://localhost:8080`:
1. Log in as `lars`/`changeme`.
2. Confirm the page background, card backgrounds, and text now use the new palette (light background `#F7F7F5`, white cards, rounded corners) instead of the previous unstyled look.
3. Confirm the browser's Network tab shows the four `.woff2` font files loading with `200` status (not 404) and that headings/body text visually render in Inter (not the browser's default serif/sans fallback) — compare letterforms against a known Inter sample if unsure.
4. Add a few tasks with different due dates (today, +2 days, +10 days, and a task with a due date in the past if you can set one) and confirm they appear grouped under "Überfällig"/"Heute"/"Diese Woche"/"Später" section headers, in that order, with empty sections simply absent.
5. Confirm the priority badge shows "Niedrig"/"Mittel"/"Hoch"/"Kritisch" (not "LOW"/"MEDIUM"/etc).
6. Confirm the quick-add "+" button and the mic button are both circular/dark, and that the project filter pills and project chips are fully rounded (pill-shaped).

---

### Task 5: Dark-mode manual toggle

**Files:**
- Modify: `grails-app/views/task/index.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`

**Interfaces:**
- Consumes: the `:root[data-theme="dark"]` / `:root[data-theme="light"]` CSS override blocks already defined in `_tokens.css` (Task 1).
- Produces: nothing consumed by later tasks — this is the final task in this plan.

- [ ] **Step 1: Add the toggle button next to the existing settings link**

In `grails-app/views/task/index.gsp`, replace:

```gsp
    <header class="navbar">
        <h1>Meine Tasks</h1>
        <a href="${createLink(controller: 'settings')}" aria-label="Einstellungen">⚙️</a>
    </header>
```

with:

```gsp
    <header class="navbar">
        <h1>Meine Tasks</h1>
        <div style="display:flex;gap:8px;align-items:center;">
            <button type="button" id="theme-toggle" aria-label="Darstellung umschalten">🌙</button>
            <a href="${createLink(controller: 'settings')}" aria-label="Einstellungen">⚙️</a>
        </div>
    </header>
```

- [ ] **Step 2: Add the toggle script**

In `grails-app/views/task/index.gsp`, add this script block right before the existing `<script>` block that handles the HTMX CSRF header (i.e., right after the closing `</form>` of `#quick-add` and before the `<!-- Session routes keep CSRF protection... -->` comment):

```gsp
    <script>
    (function () {
        const btn = document.getElementById('theme-toggle');
        const stored = localStorage.getItem('taskboard-theme');
        if (stored) {
            document.documentElement.setAttribute('data-theme', stored);
            btn.textContent = stored === 'dark' ? '☀️' : '🌙';
        }
        btn.addEventListener('click', function () {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const current = document.documentElement.getAttribute('data-theme') || (prefersDark ? 'dark' : 'light');
            const next = current === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('taskboard-theme', next);
            btn.textContent = next === 'dark' ? '☀️' : '🌙';
        });
    })();
    </script>
```

This reads any previously-stored preference on page load (before the HTMX/CSRF script, so the theme is set as early as possible in the page to avoid a flash of the wrong theme) and toggles + persists on click. If nothing is stored yet, the page simply follows `prefers-color-scheme` via the `@media` block in `_tokens.css` (Task 1) — no `data-theme` attribute is set at all until the user clicks the toggle once.

- [ ] **Step 3: Style the toggle button to match the other navbar icon**

In `grails-app/assets/stylesheets/taskboard.css`, add this rule right after the existing `.navbar h1` rule:

```css
#theme-toggle { background: none; border: none; font-size: 18px; cursor: pointer;
    padding: 0; color: var(--color-text); }
```

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `./gradlew test integrationTest --rerun-tasks`
Expected: BUILD SUCCESSFUL (this task is GSP/CSS/JS only, no Groovy logic changed).

- [ ] **Step 5: Commit**

```bash
git add grails-app/views/task/index.gsp grails-app/assets/stylesheets/taskboard.css
git commit -m "feat: add manual dark-mode toggle"
```

- [ ] **Step 6: Manual verification via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, then in a browser at `http://localhost:8080`:
1. Log in. With no stored preference yet, confirm the page matches your OS/browser's current light/dark setting (toggle your OS appearance setting and reload to confirm the `@media` query is actually driving it).
2. Click the 🌙/☀️ button — confirm the whole page's colors (background, cards, pills) switch immediately to the other theme, and the icon itself flips between 🌙 and ☀️.
3. Reload the page — confirm the manually-chosen theme persists (does NOT revert to the OS default), proving the `localStorage` value is being read on load.
4. Open the browser's dev tools, clear `localStorage` for the site, reload — confirm it falls back to following the OS `prefers-color-scheme` setting again.
