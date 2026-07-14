<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <link rel="manifest" href="/manifest.json">
    <title>Meine Tasks</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Meine Tasks', linkHome: false]"/>

    <div id="ios-push-hint" class="install-hint" hidden>
        <span>Für Erinnerungen: Zum Home-Bildschirm hinzufügen (Teilen-Symbol → „Zum Home-Bildschirm")</span>
        <button type="button" id="ios-push-hint-close" aria-label="Hinweis schließen">×</button>
    </div>

    <div id="push-permission-hint" class="install-hint" hidden>
        <span>Benachrichtigungen für Erinnerungen aktivieren?</span>
        <button type="button" id="push-permission-hint-action" class="install-hint-action">Aktivieren</button>
        <button type="button" id="push-permission-hint-close" aria-label="Hinweis schließen">×</button>
    </div>

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

    <!-- Session routes keep CSRF protection (see SecurityConfig.groovy); HTMX's
         own POSTs don't carry it automatically, so echo the cookie as a header. -->
    <script>
    document.body.addEventListener('htmx:configRequest', (e) => {
        const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
        if (m) e.detail.headers['X-XSRF-TOKEN'] = decodeURIComponent(m[1]);
    });

    // hx-target/hx-swap on #quick-add only replace #task-list, so the form
    // itself (and its title input) is never touched by the swap -- clear
    // it manually once the add succeeds.
    document.getElementById('quick-add').addEventListener('htmx:afterRequest', (e) => {
        if (e.detail.successful) {
            document.getElementById('title-input').value = '';
        }
    });
    </script>
    <asset:javascript src="voice.js"/>
    <asset:javascript src="push.js"/>
    <asset:javascript src="recurrence.js"/>
</body>
</html>
