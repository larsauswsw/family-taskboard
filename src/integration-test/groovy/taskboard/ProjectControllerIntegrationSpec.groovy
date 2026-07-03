package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Exercises ProjectController over real HTTP, same login-flow pattern as
 * TaskControllerSessionFlowIntegrationSpec. No @Rollback: these HTTP calls run
 * on a separate server thread, so a test-level transaction wouldn't see them
 * anyway (same reasoning as ApiTaskControllerIntegrationSpec) -- rows are
 * cleaned up explicitly instead.
 */
@Integration
class ProjectControllerIntegrationSpec extends Specification {

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

    void "unauthenticated request to /project/list redirects to login"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/project/list").openConnection()
        conn.instanceFollowRedirects = false

        expect:
        conn.responseCode == 302
        conn.getHeaderField("Location")?.contains("/login/auth")
    }

    void "create then update then delete a project via HTTP round-trips correctly"() {
        given:
        Map<String, String> cookies = loggedInCookies()

        when: "creating a project"
        def create = new URL("http://localhost:${serverPort}/project/create").openConnection()
        create.requestMethod = "POST"
        create.doOutput = true
        create.instanceFollowRedirects = false
        create.setRequestProperty("Cookie", cookieHeader(cookies))
        create.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        create.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        create.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Controller-Flow-Project', 'UTF-8')}&color=${URLEncoder.encode('#3B82F6', 'UTF-8')}"
        }
        int createStatus = create.responseCode
        String createBody = create.inputStream.text

        then:
        createStatus == 200
        createBody.contains("Controller-Flow-Project")

        when: "updating it"
        Long projectId
        Project.withTransaction { projectId = Project.findByName("Controller-Flow-Project").id }
        def update = new URL("http://localhost:${serverPort}/project/update/${projectId}").openConnection()
        update.requestMethod = "POST"
        update.doOutput = true
        update.instanceFollowRedirects = false
        update.setRequestProperty("Cookie", cookieHeader(cookies))
        update.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        update.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        update.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Renamed-Project', 'UTF-8')}&color=${URLEncoder.encode('#10B981', 'UTF-8')}"
        }
        int updateStatus = update.responseCode
        String updateBody = update.inputStream.text

        then:
        updateStatus == 200
        updateBody.contains("Renamed-Project")
        !updateBody.contains("Controller-Flow-Project")

        when: "submitting an invalid color"
        def invalid = new URL("http://localhost:${serverPort}/project/update/${projectId}").openConnection()
        invalid.requestMethod = "POST"
        invalid.doOutput = true
        invalid.instanceFollowRedirects = false
        invalid.setRequestProperty("Cookie", cookieHeader(cookies))
        invalid.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        invalid.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        invalid.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Renamed-Project', 'UTF-8')}&color=not-a-color"
        }
        String invalidBody = invalid.inputStream.text

        then: "the invalid submission is rejected and the prior valid values are unchanged"
        invalidBody.contains("Ungültiger Name oder Farbe")
        Project.withTransaction { Project.get(projectId).color == "#10B981" }

        when: "deleting it"
        def delete = new URL("http://localhost:${serverPort}/project/delete/${projectId}").openConnection()
        delete.requestMethod = "POST"
        delete.doOutput = true
        delete.instanceFollowRedirects = false
        delete.setRequestProperty("Cookie", cookieHeader(cookies))
        delete.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        delete.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        delete.outputStream.withWriter { it << "" }
        int deleteStatus = delete.responseCode
        String deleteBody = delete.inputStream.text

        then:
        deleteStatus == 200
        !deleteBody.contains("Renamed-Project")
        Project.withTransaction { Project.get(projectId) == null }
    }
}
