<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Einstellungen</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Einstellungen', linkHome: true]"/>

    <main class="settings-page">
        <p><strong>Benutzername:</strong> ${user.username}</p>
        <p><strong>API-Token:</strong></p>
        <input type="text" readonly value="${user.apiToken}" class="token-display"
               onclick="this.select()">

        <form method="post" action="${createLink(action: 'regenerateToken')}"
              onsubmit="return confirm('Der alte Token wird sofort ungültig. Bestehende Apple-Shortcuts funktionieren erst wieder nach Aktualisierung. Fortfahren?')">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <button type="submit">Token neu generieren</button>
        </form>
    </main>
</body>
</html>
