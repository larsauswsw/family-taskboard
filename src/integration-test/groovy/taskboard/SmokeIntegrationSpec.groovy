package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class SmokeIntegrationSpec extends Specification {
    void "application context loads"() {
        expect:
        true
    }
}
