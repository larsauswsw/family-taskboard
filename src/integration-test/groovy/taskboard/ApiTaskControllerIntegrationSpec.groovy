package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Transactional
import spock.lang.Specification
import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * No @Rollback here: this spec makes real HTTP calls to the embedded server,
 * which handles the request on its own thread/DB connection. Data saved inside
 * a @Rollback-wrapped transaction on the test thread is invisible to that other
 * connection (uncommitted), so fixtures must actually be committed and cleaned
 * up manually instead.
 */
@Integration
class ApiTaskControllerIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    @Transactional
    void cleanup() {
        Task.findAllByTitle("Test Aufgabe")*.delete(flush: true)
        User.findByUsername("api")?.delete(flush: true)
    }

    void "quick-add with valid token creates a task"() {
        given:
        // withTransaction opens and commits its own physical transaction so the
        // row is actually visible to the server thread handling the HTTP call below.
        User.withTransaction {
            new User(username: "api", password: "p", displayName: "Api",
                     apiToken: "valid-token").save(flush: true, failOnError: true)
        }

        def conn = new URL("http://localhost:${serverPort}/api/tasks/quick").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer valid-token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.withWriter { it << JsonOutput.toJson([text: "Test Aufgabe"]) }

        expect:
        conn.responseCode == 201
    }

    void "quick-add with bad token returns 401"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/api/tasks/quick").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer nope")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.withWriter { it << JsonOutput.toJson([text: "x"]) }

        expect:
        conn.responseCode == 401
    }
}
