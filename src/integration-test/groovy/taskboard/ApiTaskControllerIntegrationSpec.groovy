package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

@Integration
@Rollback
class ApiTaskControllerIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    void "quick-add with valid token creates a task"() {
        given:
        new User(username: "api", password: "p", displayName: "Api",
                 apiToken: "valid-token").save(flush: true)

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
