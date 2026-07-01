package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Guards against SecurityConfig silently not being registered as a Spring bean
 * (happened once: it lives under src/main/groovy and Grails does not
 * component-scan that location automatically, so it must be wired via
 * @Import(SecurityConfig) on Application.groovy). Without it, Spring Boot's
 * actuator-driven default locks every request behind generated HTTP Basic
 * credentials instead of our real permitAll/formLogin/CSRF rules.
 */
@Integration
class SecurityFilterChainIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    @Autowired
    org.springframework.context.ApplicationContext ctx

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    void "our custom SecurityConfig filter chain bean is registered"() {
        expect:
        ctx.containsBean('taskboard.SecurityConfig')
        ctx.containsBean('filterChain')
    }

    void "permitAll path is reachable without authentication"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/manifest.json").openConnection()

        expect:
        conn.responseCode != 401
    }

    void "protected path redirects unauthenticated requests to the login page, not a Basic challenge"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/task/index").openConnection()
        conn.instanceFollowRedirects = false

        expect:
        conn.responseCode == 302
        conn.getHeaderField("Location")?.contains("/login/auth")
    }
}
