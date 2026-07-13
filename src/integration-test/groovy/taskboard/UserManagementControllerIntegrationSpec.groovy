package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Exercises UserManagementController over real HTTP, same login-flow pattern
 * as SettingsControllerIntegrationSpec/ProjectControllerIntegrationSpec. No
 * @Rollback: these HTTP calls run on a separate server thread, so a
 * test-level transaction wouldn't see them anyway -- test users are created
 * directly via GORM with flush:true and cleaned up afterwards.
 */
@Integration
class UserManagementControllerIntegrationSpec extends Specification {

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

    private static int post(String url, Map<String, String> cookies, Map<String, String> form) {
        def conn = new URL(url).openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Cookie", cookieHeader(cookies))
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String body = (form.collect { k, v -> "${k}=${URLEncoder.encode(v, 'UTF-8')}" } +
            ["_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"]).join('&')
        conn.outputStream.withWriter { it << body }
        conn.responseCode
    }

    void "non-admin user is redirected to the access-denied page"() {
        given:
        User.withTransaction {
            new User(username: "um-member", password: passwordEncoder.encode("member-pw"),
                displayName: "Member", apiToken: "um-member-t").save(flush: true)
        }
        Map<String, String> cookies = loggedInCookies("um-member", "member-pw")

        when: "Spring Security's accessDeniedPage sets 403 and does a server-side forward to /login/denied -- not a redirect. Body is deliberately not read here: HttpURLConnection.getInputStream() throws for any 4xx/5xx status, and the security-relevant fact is the status code, not the rendered page."
        def conn = new URL("http://localhost:${serverPort}/userManagement").openConnection()
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Cookie", cookieHeader(cookies))
        int status = conn.responseCode

        then:
        status == 403

        cleanup:
        User.withTransaction { User.findByUsername("um-member")?.delete(flush: true) }
    }

    void "admin can create, edit and delete a family member end-to-end"() {
        given: "logged in as the seeded admin"
        Map<String, String> cookies = loggedInCookies("lars", "changeme")

        when: "creating a new family member"
        int createStatus = post("http://localhost:${serverPort}/userManagement/create", cookies,
            [username: "um-newkid", displayName: "New Kid", password: "family-pw-1", admin: "false"])
        String afterCreate
        User.withTransaction {
            def show = new URL("http://localhost:${serverPort}/userManagement").openConnection()
            show.setRequestProperty("Cookie", cookieHeader(cookies))
            afterCreate = show.inputStream.text
        }

        then:
        createStatus == 200
        afterCreate.contains("um-newkid")
        User.withTransaction { User.findByUsername("um-newkid") != null }

        when: "editing that family member to promote them to admin"
        Long id
        User.withTransaction { id = User.findByUsername("um-newkid").id }
        int updateStatus = post("http://localhost:${serverPort}/userManagement/update/${id}", cookies,
            [displayName: "New Kid Updated", admin: "true"])

        then:
        updateStatus == 200
        User.withTransaction { User.get(id).displayName == "New Kid Updated" && User.get(id).admin == true }

        when: "deleting that family member"
        int deleteStatus = post("http://localhost:${serverPort}/userManagement/delete/${id}", cookies, [:])

        then:
        deleteStatus == 200
        User.withTransaction { User.get(id) == null }
    }

    void "admin cannot delete their own account through the management screen"() {
        given:
        Map<String, String> cookies = loggedInCookies("lars", "changeme")
        Long larsId
        User.withTransaction { larsId = User.findByUsername("lars").id }

        when:
        def conn = new URL("http://localhost:${serverPort}/userManagement/delete/${larsId}").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Cookie", cookieHeader(cookies))
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        conn.outputStream.withWriter { it << "_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}" }
        String body = conn.inputStream.text

        then:
        body.contains("Der eigene Account kann nicht gelöscht werden.")
        User.withTransaction { User.findByUsername("lars") != null }
    }
}
