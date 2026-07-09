# Mic-Button Recording Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the quick-add mic button real-time recording feedback — an animated stop icon with a live elapsed-time counter while listening, click-to-stop, and specific German error messages on failure.

**Architecture:** `voice.js` gains an explicit `state` variable (`idle` / `listening` / `error`) plus small `goIdle()`/`goListening()`/`goError()` transition functions that each own their DOM updates (icon, `aria-label`, CSS class, input placeholder) and timer bookkeeping (`setInterval` for the live counter, `setTimeout` for the error auto-revert). `taskboard.css` gains a `.fab.listening` rule using the existing `--color-urgency-red` token plus a `@keyframes` pulse animation. No Groovy/GSP/controller changes — entirely client-side.

**Tech Stack:** Vanilla JS (Web Speech API), plain CSS with existing custom-property tokens — no new runtime dependencies, no build tooling.

## Global Constraints

- Files touched: `grails-app/assets/javascripts/voice.js` (full rewrite) and `grails-app/assets/stylesheets/taskboard.css` (append only) — nothing else. Do NOT touch `_tokens.css`, any `.gsp`, or any Groovy file.
- No new color tokens — reuse the existing `--color-urgency-red` custom property (already defined in both light and dark blocks of `_tokens.css`).
- German-language user-facing text only. Exact strings (copied verbatim from the spec, do not rephrase):
  - Idle placeholder: `Neuer Task…` (already the existing placeholder text in `index.gsp` — do not change it there, `voice.js` just needs to restore this exact string on transitions back to idle)
  - Listening placeholder: `Höre zu… (` + elapsed `m:ss` + `)`
  - Idle `aria-label`: `Per Sprache hinzufügen`
  - Listening `aria-label`: `Aufnahme stoppen`
  - Error messages: `Nichts gehört` (`no-speech`), `Mikrofon blockiert` (`not-allowed` and `service-not-allowed`), `Kein Mikrofon gefunden` (`audio-capture`), `Netzwerkfehler` (`network`), `Fehler, bitte erneut versuchen` (fallback for any other code)
- Error messages are shown in the input's `placeholder` attribute for exactly 5000ms, then auto-revert to idle.
- The Web Speech API cannot be driven by Spock or by automated browser tooling (requires a real microphone permission grant) — no new automated tests are added for this feature. Verification is manual, per Task 1 Step 8 below.
- `recognition.stop()` (graceful) is used for the manual click-to-stop, never `recognition.abort()`.

---

### Task 1: State machine, timer, error handling, and pulse styling

**Files:**
- Modify (full replace): `grails-app/assets/javascripts/voice.js`
- Modify (append): `grails-app/assets/stylesheets/taskboard.css`

**Interfaces:**
- Consumes: `#mic-btn` and `#title-input` DOM elements (already rendered by `index.gsp`, unchanged), the existing `--color-urgency-red` CSS custom property from `_tokens.css`.
- Produces: nothing consumed by other tasks — this is the only task in this plan.

- [ ] **Step 1: Replace the full contents of `voice.js`**

Replace the full contents of `grails-app/assets/javascripts/voice.js` with:

```javascript
// In-app "comfort path" voice dictation into the quick-add title field, via the
// browser's Web Speech API. The "fastest path" for adding a task is the
// separate Apple Shortcuts REST integration (see docs/apple-shortcut.md).
(function () {
  const btn = document.getElementById('mic-btn');
  const input = document.getElementById('title-input');
  if (!btn || !input) return;

  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) { btn.style.display = 'none'; return; } // no Web Speech API support

  const recognition = new SR();
  recognition.lang = 'de-DE';
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;

  const IDLE_PLACEHOLDER = 'Neuer Task…';
  const ERROR_MESSAGES = {
    'no-speech': 'Nichts gehört',
    'not-allowed': 'Mikrofon blockiert',
    'service-not-allowed': 'Mikrofon blockiert',
    'audio-capture': 'Kein Mikrofon gefunden',
    'network': 'Netzwerkfehler'
  };
  const DEFAULT_ERROR_MESSAGE = 'Fehler, bitte erneut versuchen';

  // One of 'idle' | 'listening' | 'error'. Read by the click handler (to decide
  // start vs. stop) and by the 'end' handler (to avoid clobbering an error
  // state that a same-tick 'error' event already transitioned into).
  let state = 'idle';
  // Set right before a user-initiated recognition.stop(); lets the 'error'
  // handler distinguish a real failure from the 'aborted' error some browsers
  // fire after a graceful manual stop.
  let manualStop = false;
  let startTime = null;
  let timerId = null;
  let errorRevertId = null;

  function formatElapsed(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes + ':' + (seconds < 10 ? '0' + seconds : seconds);
  }

  function clearTimers() {
    if (timerId) { clearInterval(timerId); timerId = null; }
    if (errorRevertId) { clearTimeout(errorRevertId); errorRevertId = null; }
  }

  function goIdle() {
    clearTimers();
    state = 'idle';
    btn.textContent = '🎤';
    btn.setAttribute('aria-label', 'Per Sprache hinzufügen');
    btn.classList.remove('listening');
    input.placeholder = IDLE_PLACEHOLDER;
  }

  function goListening() {
    clearTimers();
    state = 'listening';
    manualStop = false;
    startTime = Date.now();
    btn.textContent = '⏹️';
    btn.setAttribute('aria-label', 'Aufnahme stoppen');
    btn.classList.add('listening');
    input.placeholder = 'Höre zu… (0:00)';
    timerId = setInterval(function () {
      input.placeholder = 'Höre zu… (' + formatElapsed(Date.now() - startTime) + ')';
    }, 1000);
    recognition.start();
  }

  function goError(message) {
    clearTimers();
    state = 'error';
    btn.textContent = '🎤';
    btn.setAttribute('aria-label', 'Per Sprache hinzufügen');
    btn.classList.remove('listening');
    input.placeholder = message;
    errorRevertId = setTimeout(goIdle, 5000);
  }

  btn.addEventListener('click', function () {
    if (state === 'listening') {
      manualStop = true;
      recognition.stop();
    } else {
      goListening();
    }
  });

  recognition.addEventListener('result', function (e) {
    input.value = e.results[0][0].transcript;
    goIdle();
    input.focus();
  });

  recognition.addEventListener('end', function () {
    if (state === 'listening') { goIdle(); }
  });

  recognition.addEventListener('error', function (e) {
    if (manualStop && e.error === 'aborted') { return; }
    goError(ERROR_MESSAGES[e.error] || DEFAULT_ERROR_MESSAGE);
  });
})();
```

- [ ] **Step 2: Append the pulse styling to `taskboard.css`**

In `grails-app/assets/stylesheets/taskboard.css`, find this existing line:

```css
.fab { position: fixed; bottom: 16px; right: 16px; width: 56px; height: 56px; font-size: 22px; }
```

Immediately after it, add:

```css
.fab.listening { background: var(--color-urgency-red);
    animation: mic-pulse 1.5s ease-out infinite; }

@keyframes mic-pulse {
    0%   { box-shadow: 0 0 0 0 rgba(217, 66, 46, 0.5); }
    100% { box-shadow: 0 0 0 16px rgba(217, 66, 46, 0); }
}
```

- [ ] **Step 3: Start the app and load the task list in a browser**

Run: `./gradlew bootRun -Dgrails.env=test`, log in as `lars`/`changeme` at `http://localhost:8080`, and confirm the page loads with no JavaScript console errors (open browser dev tools → Console tab — this is the same check used for every prior JS change in this project).

- [ ] **Step 4: Manually simulate the `listening` state via dev tools (no real mic permission needed)**

In the browser console, run:

```javascript
document.getElementById('mic-btn').classList.add('listening');
```

Confirm visually: the button's background turns red (`--color-urgency-red`) and pulses with an expanding, fading ring — matching the `@keyframes mic-pulse` rule. Then run:

```javascript
document.getElementById('mic-btn').classList.remove('listening');
```

Confirm the button returns to its normal dark circular appearance.

- [ ] **Step 5: Manually verify the state machine's DOM transitions via dev tools**

In the browser console, run each of these one at a time and confirm the described result — this exercises the actual `voice.js` functions without needing a real microphone grant:

```javascript
// Simulate entering the listening state directly (bypasses recognition.start()
// itself, which needs a real mic permission grant, but exercises the same
// goListening() DOM/timer logic since it's invoked internally by the click handler).
document.getElementById('mic-btn').click();
```
Expected: button icon becomes `⏹️`, `aria-label` becomes "Aufnahme stoppen", input placeholder becomes `Höre zu… (0:00)` and then increments every second (`0:01`, `0:02`, ...). (The underlying `recognition.start()` call will likely immediately fail with a permission-denial `error` event in a headless/no-mic environment — if so, confirm the button reverts to `🎤` and the input placeholder shows `Mikrofon blockiert` for 5 seconds, then reverts to `Neuer Task…`. Either outcome — a live-incrementing timer, or an immediate permission error — confirms the state machine is wired correctly; both are valid depending on the environment's mic availability.)

- [ ] **Step 6: Run the full Grails test suite as a regression check**

Run: `./gradlew test integrationTest --rerun-tasks`

This task touches no Groovy code, so this is a pure regression check, not new coverage. Report the TRUE merged total by checking `build/reports/tests/index.html`'s merged counters directly (grep the HTML counter divs, do not just read console tail text) — known baseline going in is 116 tests, 0 failures; expect it to still read 116/0 since nothing server-side changed. Do NOT report a single suite's subtotal as if it were the combined total.

- [ ] **Step 7: Commit**

```bash
git add grails-app/assets/javascripts/voice.js grails-app/assets/stylesheets/taskboard.css
git commit -m "feat: add recording timer, stop button, and error feedback to mic button"
```

- [ ] **Step 8: Flag for real-browser follow-up verification**

This step is informational, not something to execute as part of this task — record it in your completion report. The Web Speech API's actual recognition behavior (real speech captured, real stop-button click ending a live recording, a real "no speech detected" timeout) cannot be verified by automated tooling or by a preview browser without microphone hardware access. After this task is merged, a human needs to try it in a real browser (Safari or Chrome, with microphone permission granted) to confirm: the timer counts up visibly during real speech, clicking the stop icon mid-recording ends it and reverts the button, and denying/blocking the microphone produces the "Mikrofon blockiert" message.

---

## Self-Review Notes

- **Spec coverage:** §1 (button states) → Step 1's `state` variable + `goIdle`/`goListening`/`goError`. §2 (timer) → `formatElapsed`/`timerId` in `goListening`. §3 (error handling) → `ERROR_MESSAGES` map + `manualStop` suppression of `aborted`. §4 (visual style) → Step 2's CSS. §5 (edge cases) → double-stop is naturally safe since `recognition.stop()` is idempotent-ish and the click handler only calls it while `state === 'listening'`; click-during-error-window is handled because `goListening()` unconditionally calls `clearTimers()` first, canceling the pending `errorRevertId`. §6 (testing) → Steps 3-6 and Step 8.
- **Placeholder scan:** none found — every step has literal, complete code.
- **Type/name consistency:** `state`, `manualStop`, `startTime`, `timerId`, `errorRevertId`, `goIdle`, `goListening`, `goError`, `formatElapsed`, `clearTimers` are each defined once and used consistently; no naming drift between steps (this is a single-task plan, so there's no cross-task interface to drift).
