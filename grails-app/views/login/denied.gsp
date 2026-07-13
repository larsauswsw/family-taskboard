<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Kein Zugriff</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body class="login-page">
    <div class="login-card">
        <div class="login-badge">!</div>
        <h1>Kein Zugriff</h1>
        <p class="login-error">Diese Seite ist nur für Administratoren zugänglich.</p>
        <a href="${createLink(controller: 'task')}" class="login-submit"
           style="display:block; text-align:center; text-decoration:none; box-sizing:border-box;">Zurück zur Übersicht</a>
    </div>
</body>
</html>
