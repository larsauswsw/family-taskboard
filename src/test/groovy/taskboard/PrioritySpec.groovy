package taskboard

import spock.lang.Specification
import spock.lang.Unroll

class PrioritySpec extends Specification {

    @Unroll
    void "#priority has German label #expected"() {
        expect:
        priority.germanLabel == expected

        where:
        priority          | expected
        Priority.LOW      | "Niedrig"
        Priority.MEDIUM   | "Mittel"
        Priority.HIGH     | "Hoch"
        Priority.CRITICAL | "Kritisch"
    }
}
