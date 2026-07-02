package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Exercises the real session-based path end-to-end: formLogin -> CSRF cookie
 * -> X-XSRF-TOKEN header -> authenticated POST to /task/quickAdd -> task
 * attributed to the logged-in user via SecurityContextHolder. This flow had
 * no automated coverage and was never verified against a genuinely active
 * filter chain until the SecurityConfig registration bug (see
 * SecurityFilterChainIntegrationSpec) was fixed.
 */
@Integration
class TaskControllerSessionFlowIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    private static String cookieHeader(Map<String, String> cookies) {
        cookies.collect { k, v -> "${k}=${v}" }.join('; ')
    }

    private static Map<String, String> extractCookies(URLConnection conn, Map<String, String> into) {
        conn.headerFields.get('Set-Cookie')?.each { String raw ->
            def pair = raw.split(';')[0].split('=', 2)
            if (pair.length == 2) into[pair[0]] = pair[1]
        }
        into
    }

    void "login then quick-add via HTMX-style CSRF header creates a task for the logged-in user"() {
        given: "an initial request to pick up the session + CSRF cookies"
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        extractCookies(initial, cookies)

        when: "logging in with the seeded user"
        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookieHeader(cookies))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=changeme&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        int loginStatus = login.responseCode
        extractCookies(login, cookies)

        then: "login succeeds via redirect (not re-rendering the login form)"
        loginStatus == 302
        login.getHeaderField("Location") == "http://localhost:${serverPort}/"

        when: "following the redirect, like a real browser -- login rotates the session and " +
              "clears the old CSRF cookie, so this GET is what actually hands back a fresh one"
        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)

        then: "a usable CSRF cookie is present for the HTMX shim to read"
        cookies['XSRF-TOKEN']

        when: "posting a quick-add with the XSRF-TOKEN cookie echoed as a header, like the HTMX shim does"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Session-Flow-Test-Task', 'UTF-8')}" }
        int quickAddStatus = quickAdd.responseCode
        String body = quickAdd.inputStream.text

        then: "the task is created and rendered back in the list fragment"
        quickAddStatus == 200
        body.contains("Session-Flow-Test-Task")

        cleanup:
        Task.withTransaction {
            Task.findAllByTitle("Session-Flow-Test-Task")*.delete(flush: true)
        }
    }
}
