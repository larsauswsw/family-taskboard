package taskboard

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Unroll
import java.time.LocalDate

class UrgencyServiceSpec extends Specification implements ServiceUnitTest<UrgencyService> {

    private Task task(int daysOut, Priority p) {
        new Task(title: "t", dueDate: LocalDate.of(2026,1,1).plusDays(daysOut),
                 priority: p)
    }

    @Unroll
    void "#days days out with #prio is #expected"() {
        given:
        def today = LocalDate.of(2026,1,1)
        def cfg = new UrgencyConfig()
        expect:
        service.colorFor(task(days, prio), today, cfg) == expected

        where:
        days | prio             | expected
        20   | Priority.LOW     | 'green'
        10   | Priority.LOW     | 'yellow'
        5    | Priority.LOW     | 'orange'
        2    | Priority.LOW     | 'red'
        0    | Priority.LOW     | 'red'
        -1   | Priority.LOW     | 'darkred'
        6    | Priority.CRITICAL| 'orange'
    }

    @Unroll
    void "#daysOut days out gets bucket #expected"() {
        given:
        def today = LocalDate.of(2026,1,1)

        expect:
        service.bucketFor(task(daysOut, Priority.LOW), today) == expected

        where:
        daysOut | expected
        -1      | "Überfällig"
        0       | "Heute"
        1       | "Diese Woche"
        6       | "Diese Woche"
        7       | "Später"
    }
}
