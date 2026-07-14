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

        <h2 class="section-heading">Passwort ändern</h2>
        <g:if test="${flash.passwordError}">
            <p class="project-error">${flash.passwordError}</p>
        </g:if>
        <g:if test="${flash.passwordSuccess}">
            <p>${flash.passwordSuccess}</p>
        </g:if>
        <form method="post" action="${createLink(action: 'changePassword')}" class="manage-create-form">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <input type="password" name="currentPassword" placeholder="Aktuelles Passwort" required>
            <input type="password" name="newPassword" placeholder="Neues Passwort (mind. 8 Zeichen)" required>
            <input type="password" name="newPasswordConfirm" placeholder="Neues Passwort bestätigen" required>
            <button type="submit">Passwort ändern</button>
        </form>

        <h2 class="section-heading">Erinnerungen</h2>
        <g:if test="${flash.notificationError}">
            <p class="project-error">${flash.notificationError}</p>
        </g:if>
        <g:if test="${flash.notificationSuccess}">
            <p>${flash.notificationSuccess}</p>
        </g:if>
        <form method="post" action="${createLink(action: 'updateNotificationPrefs')}" class="manage-create-form">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <label class="checkbox-field">
                <input type="checkbox" name="notifyOnDueDate" value="true" ${user.notifyOnDueDate ? 'checked' : ''}>
                Erinnerung an Fälligkeitstermine
            </label>
            <label>
                Tage im Voraus:
                <input type="number" name="notifyDaysBefore" value="${user.notifyDaysBefore}" min="0" max="30">
            </label>
            <button type="submit">Speichern</button>
        </form>

        <g:if test="${user.admin}">
            <h2 class="section-heading">Familie</h2>
            <a href="${createLink(controller: 'userManagement')}" class="btn-solid">Nutzerverwaltung</a>
        </g:if>
    </main>
</body>
</html>
