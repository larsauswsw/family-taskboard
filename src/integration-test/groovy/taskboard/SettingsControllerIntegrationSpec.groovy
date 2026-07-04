package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Exercises SettingsController over real HTTP, same login-flow pattern as
 * ProjectControllerIntegrationSpec. No @Rollback: these HTTP calls run on a
 * separate server thread, so a test-level transaction wouldn't see them
 * anyway. Uses the seeded 'lars' user (from BootStrap) directly rather than
 * creating a new one, since this controller only ever acts on "the current
 * user" -- the token is restored in `cleanup:` so other tests/manual use of
 * the seeded user aren't disrupted afterwards.
 */
@Integration
class SettingsControllerIntegrationSpec extends Specification {

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

    private Map<String, String> loggedInCookies() {
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        extractCookies(initial, cookies)

        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookieHeader(cookies))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=changeme&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        login.responseCode
        extractCookies(login, cookies)

        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)
        cookies
    }

    void "unauthenticated request to /settings redirects to login"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/settings").openConnection()
        conn.instanceFollowRedirects = false

        expect:
        conn.responseCode == 302
        conn.getHeaderField("Location")?.contains("/login/auth")
    }

    void "shows username and current token, then regenerating replaces the token shown on the page"() {
        given:
        Map<String, String> cookies = loggedInCookies()
        String originalToken
        User.withTransaction { originalToken = User.findByUsername("lars").apiToken }

        when: "loading the settings page"
        def show = new URL("http://localhost:${serverPort}/settings").openConnection()
        show.setRequestProperty("Cookie", cookieHeader(cookies))
        int showStatus = show.responseCode
        String showBody = show.inputStream.text
        extractCookies(show, cookies)

        then: "it shows the username and the current token"
        showStatus == 200
        showBody.contains("lars")
        showBody.contains(originalToken)

        when: "regenerating the token, submitting _csrf as a form field exactly like the login form does"
        def regenerate = new URL("http://localhost:${serverPort}/settings/regenerateToken").openConnection()
        regenerate.requestMethod = "POST"
        regenerate.doOutput = true
        regenerate.instanceFollowRedirects = false
        regenerate.setRequestProperty("Cookie", cookieHeader(cookies))
        regenerate.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfParam = cookies['XSRF-TOKEN']
        regenerate.outputStream.withWriter { it << "_csrf=${URLEncoder.encode(csrfParam, 'UTF-8')}" }
        int regenerateStatus = regenerate.responseCode
        extractCookies(regenerate, cookies)

        then: "it redirects back to the settings page"
        regenerateStatus == 302

        when: "loading the settings page again"
        def after = new URL("http://localhost:${serverPort}/settings").openConnection()
        after.setRequestProperty("Cookie", cookieHeader(cookies))
        String afterBody = after.inputStream.text

        then: "the shown token is different from the original"
        !afterBody.contains(originalToken)

        cleanup: "restore a known token so other tests/manual use of the seeded user aren't disrupted"
        User.withTransaction {
            def lars = User.findByUsername("lars")
            lars.apiToken = originalToken
            lars.save(flush: true, failOnError: true)
        }
    }
}
