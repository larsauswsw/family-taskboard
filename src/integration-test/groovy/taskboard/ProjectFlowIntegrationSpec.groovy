package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * End-to-end HTTP flow tying Project and Task together: create a project,
 * quick-add a task into it, filter the list by that project's pill, then
 * delete the project and confirm the task survives with no project. Matches
 * the scenario required by the Phase 2 design spec §6.
 */
@Integration
class ProjectFlowIntegrationSpec extends Specification {

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

    void "create project, quick-add a task into it, filter by it, then delete the project"() {
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

        when: "creating a project"
        def create = new URL("http://localhost:${serverPort}/project/create").openConnection()
        create.requestMethod = "POST"
        create.doOutput = true
        create.instanceFollowRedirects = false
        create.setRequestProperty("Cookie", cookieHeader(cookies))
        create.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        create.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        create.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Flow-Project', 'UTF-8')}&color=${URLEncoder.encode('#3B82F6', 'UTF-8')}"
        }
        create.responseCode
        extractCookies(create, cookies)

        Long projectId
        Project.withTransaction { projectId = Project.findByName("Flow-Project").id }

        and: "quick-adding a task into that project"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter {
            it << "title=${URLEncoder.encode('Flow-Task-In-Project', 'UTF-8')}&project=${projectId}"
        }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)

        and: "quick-adding a second task NOT in that project, so filtering can be verified"
        def quickAdd2 = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd2.requestMethod = "POST"
        quickAdd2.doOutput = true
        quickAdd2.instanceFollowRedirects = false
        quickAdd2.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd2.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd2.outputStream.withWriter {
            it << "title=${URLEncoder.encode('Flow-Task-Outside-Project', 'UTF-8')}"
        }
        quickAdd2.responseCode
        extractCookies(quickAdd2, cookies)

        then: "the task was created with the project set"
        Task.withTransaction { Task.findByTitle("Flow-Task-In-Project").project?.id == projectId }

        when: "filtering the list by that project's pill"
        def filtered = new URL("http://localhost:${serverPort}/task/list?project=${projectId}").openConnection()
        filtered.setRequestProperty("Cookie", cookieHeader(cookies))
        String filteredBody = filtered.inputStream.text

        then: "only the task in that project appears"
        filteredBody.contains("Flow-Task-In-Project")
        !filteredBody.contains("Flow-Task-Outside-Project")

        when: "deleting the project"
        def delete = new URL("http://localhost:${serverPort}/project/delete/${projectId}").openConnection()
        delete.requestMethod = "POST"
        delete.doOutput = true
        delete.instanceFollowRedirects = false
        delete.setRequestProperty("Cookie", cookieHeader(cookies))
        delete.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        delete.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        delete.outputStream.withWriter { it << "" }
        delete.responseCode

        then: "the task still exists, now with no project"
        Task.withTransaction { Task.findByTitle("Flow-Task-In-Project").project == null }

        when: "filtering by the now-deleted project id falls back to the unfiltered list"
        def staleFilter = new URL("http://localhost:${serverPort}/task/list?project=${projectId}").openConnection()
        staleFilter.setRequestProperty("Cookie", cookieHeader(cookies))
        int staleFilterStatus = staleFilter.responseCode
        String staleFilterBody = staleFilter.inputStream.text

        then:
        staleFilterStatus == 200
        staleFilterBody.contains("Flow-Task-In-Project")
        staleFilterBody.contains("Flow-Task-Outside-Project")

        cleanup:
        Task.withTransaction {
            Task.findAllByTitleInList(["Flow-Task-In-Project", "Flow-Task-Outside-Project"])*.delete(flush: true)
        }
    }
}
