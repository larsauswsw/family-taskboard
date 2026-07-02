(function () {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;

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

  navigator.serviceWorker.register('/sw.js').then(async function (reg) {
    const res = await fetch('/push/key');
    const { publicKey } = await res.json();
    if (!publicKey) return;
    const perm = await Notification.requestPermission();
    if (perm !== 'granted') return;
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey)
    });
    await fetch('/push/subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': getCsrfToken() },
      body: JSON.stringify(sub)
    });
  });
})();
