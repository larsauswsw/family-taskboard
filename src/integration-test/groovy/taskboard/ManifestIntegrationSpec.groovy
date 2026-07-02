package taskboard

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import spock.lang.Specification

@Integration
class ManifestIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    void "manifest.json is reachable and is valid JSON"() {
        when:
        def text = new URL("http://localhost:${serverPort}/manifest.json").text
        then:
        text.contains('"display"')
        text.contains('standalone')
    }
}
