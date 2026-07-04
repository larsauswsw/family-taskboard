<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <link rel="manifest" href="/manifest.json">
    <title>Meine Tasks</title>
    <asset:stylesheet src="taskboard.css"/>
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
</head>
<body>
    <header class="navbar"><h1>Meine Tasks</h1></header>

    <details id="project-manage-section">
        <summary>Projekte verwalten</summary>
        <g:render template="/project/manage" model="[projects: projects, error: null]"/>
    </details>

    <main id="task-list">
        <g:render template="list"
            model="[tasks: tasks, urgencyService: urgencyService, today: today, users: users, projects: projects, selectedProject: selectedProject]"/>
    </main>

    <form id="quick-add" hx-post="${createLink(action: 'quickAdd')}"
          hx-target="#task-list" hx-swap="innerHTML">
        <input type="text" name="title" id="title-input" placeholder="Neuer Task…" required>
        <select name="project">
            <option value="">Kein Projekt</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}">${p.name}</option>
            </g:each>
        </select>
        <button type="button" id="mic-btn" class="fab" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit">+</button>
    </form>

    <!-- Session routes keep CSRF protection (see SecurityConfig.groovy); HTMX's
         own POSTs don't carry it automatically, so echo the cookie as a header. -->
    <script>
    document.body.addEventListener('htmx:configRequest', (e) => {
        const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        if (m) e.detail.headers['X-XSRF-TOKEN'] = decodeURIComponent(m[1]);
    });
    </script>
    <asset:javascript src="voice.js"/>
    <asset:javascript src="push.js"/>
    <asset:javascript src="recurrence.js"/>
</body>
</html>
