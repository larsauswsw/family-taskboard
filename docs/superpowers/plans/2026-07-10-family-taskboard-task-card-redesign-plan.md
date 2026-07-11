# Task Card Layout Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give task cards on "Meine Tasks" real breathing room from the screen edge and each other, fix metadata into two grid rows so a missing field never shifts its neighbors, turn the project/assignee `<select>` elements into chip-styled controls instead of boxy form fields, and move the complete button into a corner badge.

**Architecture:** Pure presentation change. `_card.gsp` is restructured (no new fields, same three HTMX endpoints — `complete`, `assign`, `assignProject` — unchanged) into: a title with a corner complete-button, a 2-column CSS Grid row for project + assignee (the `<select>` elements themselves, restyled as chips), and a 3-column CSS Grid row for date + priority + recurrence icon. `taskboard.css` gains grid/chip rules and updated spacing values; three now-unused rules (`.task-meta`, `.project-chip`, `.recurrence-badge`) are removed. No Groovy, controller, or JS changes.

**Tech Stack:** Grails GSP templates, plain CSS (CSS Grid, existing custom-property tokens from `_tokens.css`) — no new runtime dependencies.

## Global Constraints

- Files touched: `grails-app/views/task/_card.gsp` (full rewrite) and `grails-app/assets/stylesheets/taskboard.css` (edit in place) — nothing else. Do NOT touch `_tokens.css`, `_list.gsp`, `index.gsp`, or any Groovy file.
- No new color tokens. Date/priority/assignee chips use the existing `--color-pill-bg` / `--color-pill-text` tokens. Only the project chip carries color, via `task.project.color` exactly as today.
- The three existing HTMX endpoints and their wiring must be preserved exactly: `complete` (`hx-post` on the button), `assign` (`hx-post` + `hx-trigger="change"` on the assignee select), `assignProject` (`hx-post` + `hx-trigger="change"` on the project select) — all still `hx-target="#task-list" hx-swap="innerHTML"`.
- **Deviation from the approved mockup, and why:** the mockups showed the project chip as fully invisible when a task has no project. That would hide the only way to assign a project to a project-less task directly from the card (the `assignProject` select is currently the sole such control on the card). Instead, when `task.project` is null, the select renders with the same muted "—" placeholder style already used for the unassigned-assignee chip — visible and tappable, not invisible. Flagged for the user's awareness; the grid-position guarantee (fixed column, never shifts) is unaffected.
- Recurrence icon: inline SVG, `viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"`, paths `<polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>` (Feather "repeat" icon, matches `_navbar.gsp`'s icon style) — replaces the 🔁 emoji in both the row-2 fact chip and the `<details>` summary toggle.
- No new automated tests — this codebase has no view-template test coverage (confirmed: no test references `.task-meta`, `.project-chip`, `.recurrence-badge`, or `.badge`). Verification is via preview tooling (visual + HTMX network check) and the full regression suite.
- Known baseline going in: 117 tests, 0 failures (`build/reports/tests/index.html` merged counters). This task touches no Groovy code, so the regression run must still read 117/0.

---

### Task 1: Restructure the card template and its styles

**Files:**
- Modify (full replace): `grails-app/views/task/_card.gsp`
- Modify (edit in place, several locations): `grails-app/assets/stylesheets/taskboard.css`

**Interfaces:**
- Consumes: `task`, `color`, `users`, `projects` — the exact same model variables `_card.gsp` already receives from `_list.gsp`'s `g:render template="card" model="[task: task, color: ..., users: users, projects: projects]"` call (unchanged, not touched by this task).
- Produces: nothing consumed by other tasks — this is the only task in this plan.

- [ ] **Step 1: Replace the full contents of `_card.gsp`**

Replace the full contents of `grails-app/views/task/_card.gsp` with:

```gsp
<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <button class="complete-btn" hx-post="${createLink(action: 'complete', id: task.id)}"
            hx-target="#task-list" hx-swap="innerHTML" aria-label="Als erledigt markieren">✓</button>
    <div class="task-row-assign">
        <select name="project" class="project-select"
                style="background-color: ${task.project ? task.project.color : 'var(--color-pill-bg)'}; color: ${task.project ? '#fff' : 'var(--color-pill-text)'};"
                hx-post="${createLink(action: 'assignProject', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.project ? '' : 'selected'}>—</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}" ${task.project?.id == p.id ? 'selected' : ''}>${p.name}</option>
            </g:each>
        </select>
        <select name="assignedTo" class="assignee-select"
                hx-post="${createLink(action: 'assign', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.assignedTo ? '' : 'selected'}>—</option>
            <g:each in="${users}" var="u">
                <option value="${u.id}" ${task.assignedTo?.id == u.id ? 'selected' : ''}>${u.displayName}</option>
            </g:each>
        </select>
    </div>
    <div class="task-row-facts">
        <span class="badge"><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority.germanLabel}</span>
        <g:if test="${task.recurrenceRule?.active}">
            <span class="badge recur-chip" title="Wiederholt sich">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
            </span>
        </g:if>
        <g:else>
            <span></span>
        </g:else>
    </div>
    <details class="recurrence-details">
        <summary><svg class="recur-icon-inline" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>Wiederholung</summary>
        <g:if test="${task.recurrenceRule?.active}">
            <p class="recurrence-summary">Wiederholt sich: ${task.recurrenceRule.type}</p>
            <form hx-post="${createLink(action: 'stopRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <button type="submit">Serie beenden</button>
            </form>
        </g:if>
        <g:else>
            <form class="recurrence-form"
                  hx-post="${createLink(action: 'setRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <select name="type" class="recurrence-type-select"
                        onchange="taskboardToggleRecurrenceFields(this)">
                    <option value="DAILY">Täglich</option>
                    <option value="WEEKLY">Wöchentlich</option>
                    <option value="MONTHLY">Monatlich</option>
                    <option value="WEEKDAYS">Wochentage</option>
                </select>
                <span class="recurrence-interval-field">
                    <label>alle <input type="number" name="interval" value="1" min="1"> ×</label>
                </span>
                <span class="recurrence-weekday-fields" hidden>
                    <label><input type="checkbox" name="weekday" value="MONDAY">Mo</label>
                    <label><input type="checkbox" name="weekday" value="TUESDAY">Di</label>
                    <label><input type="checkbox" name="weekday" value="WEDNESDAY">Mi</label>
                    <label><input type="checkbox" name="weekday" value="THURSDAY">Do</label>
                    <label><input type="checkbox" name="weekday" value="FRIDAY">Fr</label>
                    <label><input type="checkbox" name="weekday" value="SATURDAY">Sa</label>
                    <label><input type="checkbox" name="weekday" value="SUNDAY">So</label>
                </span>
                <button type="submit">Übernehmen</button>
            </form>
        </g:else>
    </details>
</div>
```

- [ ] **Step 2: Update `#task-list` and `.bucket-label` spacing in `taskboard.css`**

Find:

```css
.bucket-label { font-size: 12px; font-weight: 700; letter-spacing: 1.5px;
    text-transform: uppercase; color: var(--color-text-muted); margin: 16px 8px 8px; }
```

Replace with:

```css
#task-list { padding: 0 16px 16px; }

.bucket-label { font-size: 12px; font-weight: 700; letter-spacing: 1.5px;
    text-transform: uppercase; color: var(--color-text-muted); margin: 16px 0 8px; }
```

- [ ] **Step 3: Replace the card/title/meta rules with the new grid + chip rules**

Find:

```css
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
```

Replace with:

```css
.task-card { border-left: 5px solid; padding: 16px; margin: 0 0 12px;
    border-radius: 12px; background: var(--color-surface); box-shadow: var(--shadow-card);
    position: relative; }
.task-card.green   { border-left-color: var(--color-urgency-green); }
.task-card.yellow  { border-left-color: var(--color-urgency-yellow); }
.task-card.orange  { border-left-color: var(--color-urgency-orange); }
.task-card.red     { border-left-color: var(--color-urgency-red); }
.task-card.darkred { border-left-color: var(--color-urgency-darkred); }
.task-title { font-size: 15px; font-weight: 600; color: var(--color-text); margin: 0;
    padding-right: 36px; line-height: 1.3; }

.complete-btn { position: absolute; top: 12px; right: 12px; width: 26px; height: 26px;
    border-radius: 50%; border: 2px solid var(--color-pill-bg); background: var(--color-surface);
    color: var(--color-text-muted); font-size: 13px; padding: 0; cursor: pointer;
    display: inline-flex; align-items: center; justify-content: center; }

.task-row-assign { display: grid; grid-template-columns: minmax(0,1fr) 68px;
    align-items: center; column-gap: 6px; margin-top: 12px; }
.task-row-facts { display: grid; grid-template-columns: 1fr 1fr 1fr;
    align-items: center; column-gap: 6px; margin-top: 6px; }

.project-select, .assignee-select {
    appearance: none; -webkit-appearance: none; -moz-appearance: none;
    border: none; border-radius: 999px; padding: 4px 8px; font-size: 11px;
    font-family: 'Inter', system-ui, sans-serif; cursor: pointer;
    width: 100%; box-sizing: border-box; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.project-select { text-align: left; }
.assignee-select { text-align: center; background: var(--color-pill-bg); color: var(--color-pill-text); }
```

- [ ] **Step 4: Restyle `.badge` as a full pill and remove the now-unused `.project-chip`/`.recurrence-badge` rules**

Find:

```css
.badge { font-family: 'Inter', system-ui, sans-serif; background: var(--color-pill-bg);
    color: var(--color-pill-text); border-radius: 6px; padding: 2px 6px; font-size: 11px; }

.project-chip { display: inline-block; padding: 2px 10px; border-radius: 999px;
    font-size: 0.75em; color: #fff; margin-bottom: 4px; }
.project-pill { display: inline-block; padding: 4px 12px; margin: 2px;
    border-radius: 999px; background: var(--color-pill-bg); text-decoration: none;
    color: var(--color-pill-text); font-size: 0.85em; }
.project-pill.active { background: var(--color-pill-active-bg);
    color: var(--color-pill-active-text); font-weight: bold; }
```

Replace with:

```css
.badge { font-family: 'Inter', system-ui, sans-serif; background: var(--color-pill-bg);
    color: var(--color-pill-text); border-radius: 999px; padding: 4px 8px; font-size: 11px;
    text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recur-chip { display: flex; align-items: center; justify-content: center; }

.project-pill { display: inline-block; padding: 4px 12px; margin: 2px;
    border-radius: 999px; background: var(--color-pill-bg); text-decoration: none;
    color: var(--color-pill-text); font-size: 0.85em; }
.project-pill.active { background: var(--color-pill-active-bg);
    color: var(--color-pill-active-text); font-weight: bold; }
```

(`.project-chip` is dropped entirely — it was only used by the static project display this task removes from `_card.gsp`. `.project-pill`, used by the unrelated project-filter pills row, is kept unchanged.)

- [ ] **Step 5: Remove the now-unused `.recurrence-badge` rule and add the inline-icon alignment rule**

Find:

```css
.recurrence-badge { margin-left: 4px; }
.recurrence-details { margin-top: 6px; font-size: 0.85em; }
```

Replace with:

```css
.recur-icon-inline { vertical-align: -2px; margin-right: 4px; }
.recurrence-details { margin-top: 6px; font-size: 0.85em; }
```

- [ ] **Step 6: Start the app and load the task list in a browser**

Run: `./gradlew bootRun -Dgrails.env=test`, log in as `lars`/`changeme` at `http://localhost:8080`, and confirm the page loads with no JavaScript/console errors.

- [ ] **Step 7: Visually verify the grid and chip styling**

Using the browser (or preview tooling), confirm on the real rendered page:
- Cards sit 16px from the left/right screen edge and 12px apart vertically.
- Each card: title wraps around (not under) the top-right complete-button circle for a long title.
- Row 1 shows the project chip (colored, left, flexible width) and assignee chip (muted pill, fixed width, right) side by side.
- Row 2 shows date / priority / recurrence-icon-or-empty as three equal-width chips filling the card's full width.
- A task with no project shows a muted "—" chip in the project slot (not blank/invisible) — still in the same fixed column position as a colored project chip on other cards.
- A task with no recurrence shows nothing in row 2's third column (no visible empty pill), and other cards' recurrence icon is a flat two-arrow icon, not the 🔁 emoji — same for the icon next to "Wiederholung" in the collapsed details toggle.
- Toggle dark mode (existing navbar theme button) and confirm all chips remain legible (muted chips use theme-aware tokens; project chip's own color is unaffected by theme, same as before this change).

- [ ] **Step 8: Verify HTMX functionality is unchanged**

Using preview tooling (network request log) or the browser directly:
- Change the project select on a card — confirm a `POST` to `/task/assignProject/<id>` fires and the list re-renders with the new project chip color/text.
- Change the assignee select on a card — confirm a `POST` to `/task/assign/<id>` fires and the list re-renders with the new assignee chip text.
- Click the complete-button circle on a card — confirm a `POST` to `/task/complete/<id>` fires and the task disappears from the list.
- Expand a card's "Wiederholung" details panel and confirm the existing set/stop-recurrence forms still submit correctly (unchanged markup inside `<details>`, only the summary icon changed).

If `preview_click` doesn't reliably trigger a `<select>`'s `change` event in the tooling being used (a known quirk with HTMX-bound elements in this project's preview tooling, seen previously with `project/list.gsp`'s forms), dispatch the event manually, e.g.:

```javascript
(function(){
  const sel = document.querySelector('.project-select');
  sel.value = sel.options[1].value;
  sel.dispatchEvent(new Event('change', {bubbles: true}));
})()
```

- [ ] **Step 9: Run the full Grails test suite as a regression check**

Run: `./gradlew test integrationTest --rerun-tasks`

This task touches no Groovy code, so this is a pure regression check, not new coverage. Report the TRUE merged total by checking `build/reports/tests/index.html`'s merged counters directly (grep the HTML counter divs, do not just read console tail text) — known baseline going in is 117 tests, 0 failures; expect it to still read 117/0 since nothing server-side changed. Do NOT report a single suite's subtotal as if it were the combined total.

- [ ] **Step 10: Commit**

```bash
git add grails-app/views/task/_card.gsp grails-app/assets/stylesheets/taskboard.css
git commit -m "feat: redesign task cards with fixed-grid metadata and chip-styled selects"
```

---

## Self-Review Notes

- **Spec coverage:** §1 (spacing) → Steps 2-3 (`#task-list` padding, `.task-card` margin/padding). §2 (complete button) → Step 3 (`.complete-btn`, `position: relative` on `.task-card`, `padding-right` on `.task-title`). §3 (duplicate project chip removed) → Step 1 (no static `.project-chip` span in the new template) + Step 4 (rule deleted). §4 (two fixed grid rows) → Step 1 markup + Step 3 (`.task-row-assign`, `.task-row-facts`, chip-styled selects). §5 (recurrence icon) → Step 1 (SVG in both the fact chip and the details summary) + Step 5 (`.recur-icon-inline`). §6 (color tokens: no new ones) → confirmed, every new rule reuses `--color-pill-bg`/`--color-pill-text`/`--color-surface`/`--color-text-muted`, only the project chip's inline style carries `task.project.color` as before. §7 (edge cases) → Step 7's verification list covers no-project, unassigned, no-recurrence, and long-title cases explicitly. §8 (testing) → Steps 6-9.
- **Deviation flagged:** the empty-project chip is visible ("—", tappable) rather than literally invisible as mocked, to avoid removing the only card-level way to assign a project to a project-less task — called out in Global Constraints, not silently changed.
- **Placeholder scan:** none found — every step has literal, complete code.
- **Type/name consistency:** `.task-row-assign`, `.task-row-facts`, `.complete-btn`, `.project-select`, `.assignee-select`, `.badge`, `.recur-chip`, `.recur-icon-inline` are each defined once in Steps 3-5 and used consistently in Step 1's markup; no naming drift (single-task plan, no cross-task interface to drift). Confirmed via grep that `.task-meta`, `.project-chip`, and `.recurrence-badge` (all removed) have no other references anywhere in `grails-app/views` or `grails-app/assets/stylesheets`, and that `.project-pill` (kept) is a distinct, unrelated class used by the project-filter pills row.
