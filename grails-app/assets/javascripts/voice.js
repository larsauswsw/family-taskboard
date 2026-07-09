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
