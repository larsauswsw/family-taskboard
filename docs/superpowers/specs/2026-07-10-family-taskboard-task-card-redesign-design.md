# Task Card Layout Redesign — Design Spec

**Date:** 2026-07-10
**Status:** Approved by user, ready for planning

## Problem

On the "Meine Tasks" page (`grails-app/views/task/_card.gsp`, `_list.gsp`), cards sit very close to
the screen edges (8px margin) and to each other. Inside each card, metadata (assignee, project,
due date, priority, recurrence) is packed into a single dense flex row with plain `<select>`
elements that visually read as form fields rather than task information. Project is also shown
twice — once as a static, non-interactive chip under the title, and again as a separate `<select>`
in the meta row — which is redundant. There is no fixed layout: when a task has no project, isn't
assigned, or has no recurrence, the remaining chips shift position, making the list harder to scan
at a glance.

## Goal

Redesign the card so that: it has clear breathing room from the screen edge and between cards;
metadata is grouped into two fixed-position rows so each field always occupies the same spot
whether or not it has a value; the assignee/project pickers read as part of the card's information
(chips) rather than as form controls, while remaining fully functional; and the "mark complete"
action is visually prominent instead of trailing at the end of a data row.

## Scope

In scope: `grails-app/views/task/_card.gsp`, `grails-app/assets/stylesheets/taskboard.css`.

Out of scope: any backend/controller/service logic, the recurrence-editing `<details>` panel's
functionality (only its icon changes for visual consistency, see §5), the quick-add form, project
filter pills.

## Design

Explored visually with the user via mockups (`.superpowers/brainstorm/`) before this spec — the
sections below describe the approved result.

### 1. Card spacing

- The `#task-list` element (the `<main>` in `task/index.gsp` that wraps the whole list — no GSP
  change needed, just a new CSS rule) gets `padding: 0 16px 16px` (16px left/right edge, 16px
  bottom) — replaces the per-card `margin: 8px` as the source of edge spacing.
- `.task-card` keeps `margin-bottom: 12px` only (no left/right/top margin) for spacing between
  cards; the urgency bucket labels above each group already provide top spacing.
- `.task-card` padding becomes `16px` on all sides (was `12px 16px`).

### 2. Complete button

The ✓ button moves from the end of the meta row to a circular button pinned to the card's top-right
corner (`position: absolute; top: 12px; right: 12px`), 26px diameter, muted border by default. The
card becomes `position: relative` to anchor it. `.task-title` gets `padding-right` so long titles
wrap around it instead of under it. HTMX wiring (`hx-post`, `hx-target`, `hx-swap`) is unchanged —
only position and visual style move.

### 3. Removing the duplicate project display

The current standalone `.project-chip` (static, non-interactive, shown under the title) is removed.
The `<select name="project" class="project-select">` (already present for reassignment) becomes the
single source of truth for the project chip — see §4. This removes the redundant double-display of
the same project name.

### 4. Two fixed-position meta rows

Replace the single `.task-meta` flex row with two CSS Grid rows, each with fixed column tracks so a
missing value leaves an empty slot rather than shifting other chips:

**Row 1** — `grid-template-columns: minmax(0,1fr) 68px;`
- Column 1: project chip — the `project-select` element itself, styled to look like a solid chip
  (`background: task.project.color` inline style as today, white text, no visible border/arrow,
  `text-align: left`). When `task.project` is null, renders as a visible, tappable muted "—" chip
  (same styling as the assignee column's empty state) rather than being invisible — see the
  implementation plan's Global Constraints for why: the select is the only card-level control that
  can assign a project to a project-less task, so hiding it would remove that capability.
- Column 2: assignee chip — the `assignedTo-select` element, styled as a neutral pill
  (`--color-pill-bg` / `--color-pill-text`, same tokens as `.badge`/`.pill` today). Shows "—" when
  unassigned (already the existing placeholder option).

**Row 2** — `grid-template-columns: 1fr 1fr 1fr;` (three equal columns, fills the full card width
like row 1, no trailing dead space)
- Column 1: due date chip (existing `g:formatDate` output), neutral pill styling.
- Column 2: priority badge (existing `task.priority.germanLabel`), neutral pill styling.
- Column 3: recurrence indicator — only rendered when `task.recurrenceRule?.active`; empty
  otherwise. See §5 for the icon itself.

Both selects lose their default browser chrome (`appearance: none`, no border) so they read as
chips; clicking/tapping anywhere on the chip still opens the native picker and `hx-trigger="change"`
still fires the existing `assign`/`assignProject` HTMX calls — no functional change, only how the
control looks at rest.

### 5. Recurrence icon

Replace the 🔁 emoji (both in the row-2 chip and the existing `.recurrence-badge` /
`<summary>🔁 Wiederholung</summary>` toggle) with an inline flat SVG icon, matching the existing
navbar icon system in `_navbar.gsp` (`stroke="currentColor"`, `stroke-width="2"`,
`stroke-linecap/linejoin="round"`, no fill) — specifically the Feather "repeat" icon (two opposing
looping arrows). This keeps the recurrence indicator visually consistent with the rest of the app's
icon language instead of an emoji glyph that renders differently across platforms.

### 6. Color tokens

No new color tokens. Date, priority, and assignee chips reuse the existing
`--color-pill-bg`/`--color-pill-text` tokens (already theme-aware for light/dark). Only the project
chip carries color, using the project's own stored color as today. This keeps the card visually
calm — color is reserved for the urgency border and the project identity, not decorative on every
chip.

### 7. Edge cases

- **No project:** row 1 column 1 renders the select as a visible, tappable muted "—" chip (not
  invisible — see §4) so the assignee chip in column 2 stays in its fixed position and the project
  select remains usable.
- **Unassigned:** existing "—" placeholder option, shown in the neutral chip style.
- **No recurrence:** row 2 column 3 is empty; columns 1–2 keep their fixed width and position
  regardless.
- **Long title:** wraps under the reserved `padding-right` space for the complete button, never
  overlapping it.

### 8. Testing

Pure GSP/CSS change, no Groovy logic touched. No new automated tests — consistent with this
codebase's convention of not unit-testing view templates. Verification is manual/visual:

- Preview tooling: confirm the grid renders correctly for tasks with all fields present and with
  each field individually missing (no project, unassigned, no recurrence), in both light and dark
  theme, and that clicking the project/assignee chips still opens the picker and still triggers the
  existing HTMX reassignment (network request fires, list re-renders).
- No console errors introduced.

## Files touched

- `grails-app/views/task/_card.gsp` — restructure meta markup into the two-row grid, move the
  complete button, remove the duplicate project chip, swap the recurrence emoji for an SVG icon.
- `grails-app/assets/stylesheets/taskboard.css` — new grid rules for the meta rows, chip styling for
  the selects, complete-button repositioning, updated card spacing values, `#task-list` edge padding.

No Groovy, controller, or JavaScript changes.
