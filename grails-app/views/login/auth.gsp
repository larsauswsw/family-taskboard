<!DOCTYPE html>
<html>
<head><title>Anmelden</title></head>
<body>
<h1>Family Taskboard</h1>
<form action="/login" method="post">
    <label>Benutzername <input type="text" name="username"/></label>
    <label>Passwort <input type="password" name="password"/></label>
    <!-- Accessing _csrf here forces Spring Security to resolve+persist the
         deferred CSRF token (writing the XSRF-TOKEN cookie); without this,
         CookieCsrfTokenRepository never issues a cookie on a plain GET and
         the subsequent POST /login is rejected as a CSRF failure. -->
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    <button type="submit">Anmelden</button>
</form>
</body>
</html>
