<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nutzerverwaltung</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Nutzerverwaltung', linkHome: true]"/>

    <main class="settings-page">
        <g:render template="manage" model="[users: users, error: error, currentUserId: currentUserId]"/>
    </main>

    <!-- Session routes keep CSRF protection (see SecurityConfig.groovy); HTMX's
         own POSTs don't carry it automatically, so echo the cookie as a header. -->
    <script>
    document.body.addEventListener('htmx:configRequest', (e) => {
        const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        if (m) e.detail.headers['X-XSRF-TOKEN'] = decodeURIComponent(m[1]);
    });
    </script>
</body>
</html>
