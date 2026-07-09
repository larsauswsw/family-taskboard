# Mic-Button Recording Feedback — Design Spec

**Date:** 2026-07-09
**Status:** Approved by user, ready for planning

## Problem

The quick-add mic button (`grails-app/assets/javascripts/voice.js`) gives no visual feedback once
clicked: the icon stays 🎤, there's an unused `.listening` CSS class with no styling, and there is
no way to manually stop a recording — it only ends when the browser's own silence detection ends
it. If speech recognition fails (blocked mic, no speech heard, network error), nothing visible
happens at all; the button can appear to silently do nothing.

## Goal

While a recording is active: swap the mic icon to a stop icon, animate it, show an exact elapsed-time
counter, and let the user click it again to manually stop. On failure, show a short, specific German
error message, then revert automatically.

## Scope

In scope: `grails-app/assets/javascripts/voice.js`, `grails-app/assets/stylesheets/taskboard.css`
(styling for `.listening` and a new error/pulse animation), no other files.

Out of scope: fixing the iOS Safari secure-context gap that hides the mic button entirely on
plain-HTTP origins (tracked separately) — this spec only covers the feedback UX for browsers where
the button is already visible and working.

## Design

### 1. Button states

A plain JS variable (`state`) tracks one of three states — not just CSS classes, so the click
handler can decide what to do:

- **`idle`** (initial / default): icon `🎤`, `aria-label="Per Sprache hinzufügen"`. Clicking calls
  `recognition.start()`.
- **`listening`**: icon `⏹️`, `aria-label="Aufnahme stoppen"`, `.listening` class added to the
  button (drives the pulse animation, see §4). Clicking calls `recognition.stop()` (not `abort()` —
  a graceful stop still lets the browser return a final result for speech already captured, matching
  natural end-of-speech behavior).
- **`error`**: icon reverts to `🎤` immediately (same as idle), but the title input's placeholder
  shows an error message (see §3) for 5 seconds, then automatically reverts to `state = 'idle'` and
  the normal placeholder. A click during this window immediately clears the pending revert and starts
  a new recording (`recognition.start()`), same as from `idle`.

Reusing the existing (currently unstyled) `.listening` class name on the button element for the
`listening` state's CSS hook, since `voice.js` already adds/removes it — just never had a rule.

### 2. Timer

While `state === 'listening'`, the title input's `placeholder` attribute is overwritten every second
via `setInterval(…, 1000)`:

```
Höre zu… (0:03)
```

Elapsed time is computed each tick as `Math.floor((Date.now() - startTime) / 1000)`, not by
incrementing a counter — avoids drift if a tick is delayed. Format is `m:ss` (minutes with no
leading zero, seconds zero-padded to 2 digits) — e.g. `0:03`, `0:59`, `1:15`.

On any transition out of `listening` (result, manual stop, or error), the interval is cleared and
the placeholder reverts to `Neuer Task…` (or, for the error case, first shows the error message for
5 seconds — see §3).

**Known edge case, accepted as-is:** the placeholder is only visible while the input's `value` is
empty. If the user already typed text and then clicks the mic, no timer is visible during that
recording. This matches existing behavior unchanged by this spec: a successful recognition result
already unconditionally overwrites `input.value`, so pre-existing text was already going to be
replaced regardless of whether the timer was visible during capture.

### 3. Error handling

`recognition`'s `error` event (`SpeechRecognitionErrorEvent.error`) maps to a German message shown
in the input placeholder for 5 seconds:

| `error.error` code | Message |
|---|---|
| `no-speech` | `Nichts gehört` |
| `not-allowed` | `Mikrofon blockiert` |
| `service-not-allowed` | `Mikrofon blockiert` |
| `audio-capture` | `Kein Mikrofon gefunden` |
| `network` | `Netzwerkfehler` |
| anything else | `Fehler, bitte erneut versuchen` |

**Manual-stop suppression:** clicking the stop button sets a `manualStop = true` flag before calling
`recognition.stop()`. If an `error` event fires with code `aborted` while `manualStop` is true (some
browsers emit this on a graceful stop, most don't), it is treated as a normal end — no error message
shown, straight back to `idle`. The flag resets to `false` at the start of every new `recognition.start()`
call.

### 4. Visual style

Using the color tokens introduced in the design-system feature (`_tokens.css`):

- `.listening` background switches from `var(--color-text)` to `var(--color-urgency-red)` — reuses
  the same red already meaningful elsewhere (overdue tasks), signaling "recording" without inventing
  a new color.
- A `@keyframes pulse` animation on `box-shadow` (expanding, fading ring, `animation: pulse 1.5s
  ease-out infinite`) — clearly animated, not just a static color swap. Works unchanged in both light
  and dark mode since it's driven by the same custom properties.

No new color tokens are added — `--color-urgency-red` already exists in both the light and dark
blocks of `_tokens.css`.

### 5. Edge cases

- **Double-click on stop icon:** `recognition.stop()` is safe to call more than once (subsequent
  calls on an already-stopping recognizer are a no-op per the Web Speech API spec).
- **Click during the error window:** starts a fresh recording immediately; the pending 5-second
  revert-to-idle `setTimeout` is cleared so it can't fire later and stomp on the new state.
- **No `SpeechRecognition` support:** unaffected — the button is hidden exactly as today
  (`if (!SR) { btn.style.display = 'none'; return; }` stays unchanged).

### 6. Testing

The Web Speech API cannot be driven by Spock (no server-side involvement) or by automated browser
tooling (requires a real microphone permission grant in a real browser — confirmed during the
original mic-button work earlier in this project). No new automated tests are added for this feature,
consistent with `voice.js` having none today.

Verification plan:
- Mechanically checkable without a real mic grant: no JS console errors, correct DOM class/icon/
  `aria-label` transitions when the code paths are exercised, CSS renders correctly in both light and
  dark themes (can be checked via preview tooling by toggling classes directly).
- Requires a human in a real browser (Safari/Chrome) afterward: does a real recording actually show
  the animated stop icon + live timer, does clicking stop actually end recognition and revert, does a
  real error condition (e.g. denying mic permission) show the right message.

## Files touched

- `grails-app/assets/javascripts/voice.js` — state machine, timer, error handling, click-to-stop.
- `grails-app/assets/stylesheets/taskboard.css` — `.listening` background + `@keyframes pulse` rule.

No Groovy, GSP, or controller changes — this is entirely client-side.
