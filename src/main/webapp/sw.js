self.addEventListener('push', function (event) {
  const data = event.data ? event.data.json() : {};
  event.waitUntil(
    self.registration.showNotification(data.title || 'Taskboard', {
      body: data.body || '',
      icon: '/assets/icon-192.png'
    })
  );
});
