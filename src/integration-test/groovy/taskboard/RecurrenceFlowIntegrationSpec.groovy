package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import java.time.LocalDate

/**
 * End-to-end HTTP flow tying the whole recurring-tasks feature together:
 * quick-add a task, set a daily recurrence, complete it, confirm the next
 * occurrence appears with the correct due date and still-active rule, stop
 * the series, complete the new occurrence, and confirm no further task is
 * spawned. Matches the scenario required by the design spec §6.
 */
@Integration
class RecurrenceFlowIntegrationSpec extends Specification {

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

    void "quick-add, set daily recurrence, complete twice: one regeneration, then a stopped series generates nothing"() {
        given:
        Map<String, String> cookies = loggedInCookies()
        LocalDate today = LocalDate.now()

        and: "a quick-added task"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Recurrence-Flow-Task', 'UTF-8')}" }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)
        Long firstId
        Task.withTransaction { firstId = Task.findByTitle("Recurrence-Flow-Task").id }

        when: "setting a daily recurrence on it"
        def setRec = new URL("http://localhost:${serverPort}/task/setRecurrence/${firstId}").openConnection()
        setRec.requestMethod = "POST"
        setRec.doOutput = true
        setRec.instanceFollowRedirects = false
        setRec.setRequestProperty("Cookie", cookieHeader(cookies))
        setRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        setRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRec.outputStream.withWriter { it << "type=DAILY&interval=1" }
        setRec.responseCode
        extractCookies(setRec, cookies)

        and: "completing it"
        def complete1 = new URL("http://localhost:${serverPort}/task/complete/${firstId}").openConnection()
        complete1.requestMethod = "POST"
        complete1.doOutput = true
        complete1.instanceFollowRedirects = false
        complete1.setRequestProperty("Cookie", cookieHeader(cookies))
        complete1.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        complete1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        complete1.outputStream.withWriter { it << "" }
        complete1.responseCode
        extractCookies(complete1, cookies)

        // Extraction happens here, in a when/and block, not in then: -- a bare
        // Task.withTransaction {...} call as a top-level then: statement would itself
        // be auto-wrapped by Spock as a boolean condition, silently discarding every
        // statement in the closure except its last.
        and: "reading back the newly spawned occurrence"
        Long secondId
        LocalDate secondDueDate
        boolean secondActive
        int openCount
        Task.withTransaction {
            def open = Task.findAllByTitleAndStatus("Recurrence-Flow-Task", TaskStatus.OPEN)
            openCount = open.size()
            secondId = open[0].id
            secondDueDate = open[0].dueDate
            secondActive = open[0].recurrenceRule?.active
        }

        then: "exactly one new occurrence exists, due tomorrow, still recurring"
        openCount == 1
        secondDueDate == today.plusDays(1)
        secondActive

        when: "stopping the series on the new occurrence"
        def stopRec = new URL("http://localhost:${serverPort}/task/stopRecurrence/${secondId}").openConnection()
        stopRec.requestMethod = "POST"
        stopRec.doOutput = true
        stopRec.instanceFollowRedirects = false
        stopRec.setRequestProperty("Cookie", cookieHeader(cookies))
        stopRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        stopRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        stopRec.outputStream.withWriter { it << "" }
        stopRec.responseCode
        extractCookies(stopRec, cookies)

        and: "completing the (now non-recurring) second occurrence"
        def complete2 = new URL("http://localhost:${serverPort}/task/complete/${secondId}").openConnection()
        complete2.requestMethod = "POST"
        complete2.doOutput = true
        complete2.instanceFollowRedirects = false
        complete2.setRequestProperty("Cookie", cookieHeader(cookies))
        complete2.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        complete2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        complete2.outputStream.withWriter { it << "" }
        complete2.responseCode

        and: "reading back final task state"
        int finalCount
        boolean allDone
        Task.withTransaction {
            def all = Task.findAllByTitle("Recurrence-Flow-Task")
            finalCount = all.size()
            allDone = all.every { it.status == TaskStatus.DONE }
        }

        then: "no third occurrence was created -- still only the two prior tasks, both now DONE"
        finalCount == 2
        allDone

        cleanup:
        Task.withTransaction { Task.findAllByTitle("Recurrence-Flow-Task")*.delete(flush: true) }
    }
}
