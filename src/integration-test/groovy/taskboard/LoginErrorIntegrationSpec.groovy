package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Confirms the login page shows a German error message after a failed login
 * attempt (Spring Security's default form-login failure handler redirects to
 * loginPage + "?error" -- see SecurityConfig, no explicit failureUrl is set),
 * and that a fresh page load without ?error shows no such message.
 */
@Integration
class LoginErrorIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    void "a bad login redirects to the login page with an error param, which then shows a German error message"() {
        given: "an initial GET to resolve the CSRF cookie"
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        initial.headerFields.get('Set-Cookie')?.each { String raw ->
            def pair = raw.split(';')[0].split('=', 2)
            if (pair.length == 2) cookies[pair[0]] = pair[1]
        }

        when: "posting a bad password"
        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookies.collect { k, v -> "${k}=${v}" }.join('; '))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=definitely-wrong&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }

        then: "it redirects back to the login page with an error param"
        login.responseCode == 302
        login.getHeaderField("Location")?.contains("/login/auth") &&
            login.getHeaderField("Location")?.contains("error")

        when: "following that redirect"
        def errorPage = new URL(login.getHeaderField("Location")).openConnection()
        String errorBody = errorPage.inputStream.text

        then: "the error message is shown"
        errorBody.contains("Benutzername oder Passwort falsch")

        when: "loading the login page fresh, with no error param"
        def freshPage = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        String freshBody = freshPage.inputStream.text

        then: "no error message is shown"
        !freshBody.contains("Benutzername oder Passwort falsch")
    }
}
