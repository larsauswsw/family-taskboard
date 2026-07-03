package taskboard

import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * buildPayload used to be hand-built string interpolation ("{\"title\":\"${title}\"...}"),
 * which produced invalid JSON for any title/body containing a quote or backslash --
 * sw.js's event.data.json() would throw and silently drop the notification. This locks
 * in the JsonOutput-based fix.
 */
class PushServicePayloadSpec extends Specification {

    void "payload is valid JSON even when title/body contain quotes and backslashes"() {
        given:
        String title = 'Buy 6" pipe'
        String body = 'Path: C:\\tools\\'

        when:
        String payload = PushService.buildPayload(title, body)
        def parsed = new JsonSlurper().parseText(payload)

        then:
        parsed.title == title
        parsed.body == body
    }
}
