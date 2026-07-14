(function () {
  // iOS Safari only exposes the Push API to a web app added to the Home
  // Screen ("Zum Home-Bildschirm hinzufügen") -- a regular Safari tab has no
  // PushManager at all, so the code below would otherwise bail out silently
  // with zero feedback. Point iPhone/iPad users at the fix instead.
  function isIOS() {
    return /iphone|ipad|ipod/i.test(navigator.userAgent) && !window.MSStream;
  }
  function isStandalone() {
    return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  }

  const iosHint = document.getElementById('ios-push-hint');
  document.getElementById('ios-push-hint-close')?.addEventListener('click', () => {
    iosHint.hidden = true;
    localStorage.setItem('taskboard-ios-push-hint-dismissed', 'true');
  });

  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    if (iosHint && isIOS() && !isStandalone() &&
        localStorage.getItem('taskboard-ios-push-hint-dismissed') !== 'true') {
      iosHint.hidden = false;
    }
    return;
  }

  function urlBase64ToUint8Array(base64) {
    const padding = '='.repeat((4 - base64.length % 4) % 4);
    const b64 = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(b64);
    return Uint8Array.from([...raw].map(c => c.charCodeAt(0)));
  }

  // /push/subscribe is a session route with CSRF protection (see SecurityConfig.groovy
  // / index.gsp's htmx:configRequest shim). This fetch() call is raw (not HTMX), so it
  // does NOT get the header attached automatically -- read the XSRF-TOKEN cookie
  // (CookieCsrfTokenRepository.withHttpOnlyFalse() makes it readable from JS) and echo
  // it back manually as X-XSRF-TOKEN, same pattern as the HTMX shim in index.gsp.
  function getCsrfToken() {
    const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  }

  async function subscribeAndSend(reg, publicKey) {
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey)
    });
    const res = await fetch('/push/subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': getCsrfToken() },
      body: JSON.stringify(sub)
    });
    if (!res.ok) throw new Error('Abo speichern fehlgeschlagen (HTTP ' + res.status + ')');
  }

  const permHint = document.getElementById('push-permission-hint');
  document.getElementById('push-permission-hint-close')?.addEventListener('click', () => {
    permHint.hidden = true;
    localStorage.setItem('taskboard-push-hint-dismissed', 'true');
  });

  navigator.serviceWorker.register('/sw.js').then(async function (reg) {
    const res = await fetch('/push/key');
    const { publicKey } = await res.json();
    if (!publicKey) return; // no VAPID keypair configured server-side

    if (Notification.permission === 'granted') {
      await subscribeAndSend(reg, publicKey);
      return;
    }
    if (Notification.permission === 'denied') return; // JS can't re-prompt; only iOS Settings can

    // Safari (desktop and iOS) only shows the native permission dialog when
    // Notification.requestPermission() is called synchronously from a user
    // gesture -- calling it automatically on page load is silently ignored,
    // no prompt and no error. Ask via a button tap instead.
    if (permHint && localStorage.getItem('taskboard-push-hint-dismissed') !== 'true') {
      permHint.hidden = false;
      document.getElementById('push-permission-hint-action').addEventListener('click', async function () {
        permHint.hidden = true;
        const perm = await Notification.requestPermission();
        if (perm === 'granted') {
          await subscribeAndSend(reg, publicKey);
        }
      }, { once: true });
    }
  }).catch(function (err) {
    console.error('Push-Setup fehlgeschlagen:', err);
  });
})();
