<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>Anmelden</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body class="login-page">
    <div class="login-card">
        <div class="login-badge">✓</div>
        <h1>Family Taskboard</h1>
        <g:if test="${loginFailed}">
            <p class="login-error">Benutzername oder Passwort falsch</p>
        </g:if>
        <form action="/login" method="post">
            <input type="text" name="username" placeholder="Benutzername" required>
            <input type="password" name="password" placeholder="Passwort" required>
            <!-- Accessing _csrf here forces Spring Security to resolve+persist the
                 deferred CSRF token (writing the XSRF-TOKEN cookie); without this,
                 CookieCsrfTokenRepository never issues a cookie on a plain GET and
                 the subsequent POST /login is rejected as a CSRF failure. -->
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <button type="submit" class="login-submit">Anmelden</button>
        </form>
    </div>
</body>
</html>
