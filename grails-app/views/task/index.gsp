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
    <header class="navbar">
        <h1>Meine Tasks</h1>
        <div style="display:flex;gap:8px;align-items:center;">
            <button type="button" id="theme-toggle" aria-label="Darstellung umschalten">🌙</button>
            <a href="${createLink(controller: 'settings')}" aria-label="Einstellungen">⚙️</a>
        </div>
    </header>

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
        <button type="button" id="mic-btn" class="fab round-btn" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit" class="round-btn">+</button>
    </form>

    <script>
    (function () {
        const btn = document.getElementById('theme-toggle');
        const stored = localStorage.getItem('taskboard-theme');
        if (stored) {
            document.documentElement.setAttribute('data-theme', stored);
            btn.textContent = stored === 'dark' ? '☀️' : '🌙';
        }
        btn.addEventListener('click', function () {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const current = document.documentElement.getAttribute('data-theme') || (prefersDark ? 'dark' : 'light');
            const next = current === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('taskboard-theme', next);
            btn.textContent = next === 'dark' ? '☀️' : '🌙';
        });
    })();
    </script>

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
