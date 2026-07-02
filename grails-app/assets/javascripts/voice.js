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

  btn.addEventListener('click', function () {
    recognition.start();
    btn.classList.add('listening');
  });

  recognition.addEventListener('result', function (e) {
    input.value = e.results[0][0].transcript;
    btn.classList.remove('listening');
    input.focus();
  });

  recognition.addEventListener('end', function () {
    btn.classList.remove('listening');
  });
})();
