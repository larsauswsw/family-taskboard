package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Exercises PushController over real HTTP, same login-flow pattern as
 * SettingsControllerIntegrationSpec. This matters specifically for
 * subscribe(): a GORM save(flush: true) called directly from a controller
 * action has no open transaction and throws TransactionRequiredException in
 * production -- a @Rollback-wrapped test (which opens its own transaction)
 * would never have caught that, so this deliberately goes over real HTTP
 * with no @Rollback, like the other *ControllerIntegrationSpecs.
 */
@Integration
class PushControllerIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    @Autowired
    PasswordEncoder passwordEncoder

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

    private Map<String, String> loggedInCookies(String username, String password) {
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
        String form = "username=${username}&password=${URLEncoder.encode(password, 'UTF-8')}" +
            "&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        login.responseCode
        extractCookies(login, cookies)

        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)
        cookies
    }

    void "subscribing over real HTTP (as push.js does) persists the subscription"() {
        given: "a dedicated test user, not the seeded 'lars' account"
        User.withTransaction {
            new User(username: "push-http1", password: passwordEncoder.encode("push-pw"),
                displayName: "Push Test", apiToken: "push-http1-t").save(flush: true)
        }
        Map<String, String> cookies = loggedInCookies("push-http1", "push-pw")

        when: "posting a JSON body with an X-XSRF-TOKEN header, exactly like push.js's raw fetch()"
        def conn = new URL("http://localhost:${serverPort}/push/subscribe").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Cookie", cookieHeader(cookies))
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        String body = '{"endpoint":"https://push.example/ep-http1","keys":{"p256dh":"key1","auth":"auth1"}}'
        conn.outputStream.withWriter { it << body }
        int status = conn.responseCode

        then: "it succeeds instead of throwing TransactionRequiredException"
        status == 201
        User.withTransaction {
            def u = User.findByUsername("push-http1")
            PushSubscription.findAllByUser(u)*.endpoint == ["https://push.example/ep-http1"]
        }

        cleanup:
        User.withTransaction {
            def u = User.findByUsername("push-http1")
            PushSubscription.findAllByUser(u).each { it.delete(flush: true) }
            u?.delete(flush: true)
        }
    }
}
