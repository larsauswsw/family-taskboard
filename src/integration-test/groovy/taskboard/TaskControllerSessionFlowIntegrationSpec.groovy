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

    void "quick-add without a CSRF token is rejected, not silently accepted"() {
        given: "a logged-in session (same login sequence as the happy-path test above)"
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

        when: "posting a quick-add WITHOUT the X-XSRF-TOKEN header the HTMX shim normally adds"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Should-Not-Be-Created', 'UTF-8')}" }

        then:
        quickAdd.responseCode == 403
    }

    void "assigning a task via the card's select updates assignedTo and re-renders the assignee's name"() {
        given: "a logged-in session"
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

        and: "a task to assign, and a second user to assign it to"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Assign-Flow-Test-Task', 'UTF-8')}" }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)

        Long taskId
        Task.withTransaction { taskId = Task.findByTitle("Assign-Flow-Test-Task").id }
        User.withTransaction {
            new User(username: "assign-flow-u", password: "p",
                displayName: "Assign-Flow-User", apiToken: "afu").save(flush: true)
        }
        Long assigneeId
        User.withTransaction { assigneeId = User.findByUsername("assign-flow-u").id }

        when: "posting to /task/assign/<id>, exactly what the card's <select> does on change"
        def assign = new URL("http://localhost:${serverPort}/task/assign/${taskId}").openConnection()
        assign.requestMethod = "POST"
        assign.doOutput = true
        assign.instanceFollowRedirects = false
        assign.setRequestProperty("Cookie", cookieHeader(cookies))
        assign.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        assign.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        assign.outputStream.withWriter { it << "assignedTo=${assigneeId}" }
        int assignStatus = assign.responseCode
        String body = assign.inputStream.text

        then: "the response fragment shows the new assignee, and the DB reflects it"
        assignStatus == 200
        body.contains("Assign-Flow-User")
        Task.withTransaction { Task.get(taskId).assignedTo?.id == assigneeId }

        cleanup:
        Task.withTransaction {
            Task.findAllByTitle("Assign-Flow-Test-Task")*.delete(flush: true)
        }
        User.withTransaction {
            User.findAllByUsername("assign-flow-u")*.delete(flush: true)
        }
    }

    void "setting and then stopping a recurrence via HTMX round-trips correctly"() {
        given: "a logged-in session"
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

        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Recurring-Flow-Task', 'UTF-8')}" }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)
        Long taskId
        Task.withTransaction { taskId = Task.findByTitle("Recurring-Flow-Task").id }

        when: "setting a weekly recurrence"
        def setRec = new URL("http://localhost:${serverPort}/task/setRecurrence/${taskId}").openConnection()
        setRec.requestMethod = "POST"
        setRec.doOutput = true
        setRec.instanceFollowRedirects = false
        setRec.setRequestProperty("Cookie", cookieHeader(cookies))
        setRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        setRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRec.outputStream.withWriter { it << "type=WEEKLY&interval=1" }
        int setRecStatus = setRec.responseCode
        extractCookies(setRec, cookies)

        then:
        setRecStatus == 200
        Task.withTransaction { Task.get(taskId).recurrenceRule?.active }

        when: "stopping the series"
        def stopRec = new URL("http://localhost:${serverPort}/task/stopRecurrence/${taskId}").openConnection()
        stopRec.requestMethod = "POST"
        stopRec.doOutput = true
        stopRec.instanceFollowRedirects = false
        stopRec.setRequestProperty("Cookie", cookieHeader(cookies))
        stopRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        stopRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        stopRec.outputStream.withWriter { it << "" }
        int stopRecStatus = stopRec.responseCode

        then:
        stopRecStatus == 200
        Task.withTransaction { !Task.get(taskId).recurrenceRule?.active }

        cleanup:
        Task.withTransaction { Task.findAllByTitle("Recurring-Flow-Task")*.delete(flush: true) }
    }
}
