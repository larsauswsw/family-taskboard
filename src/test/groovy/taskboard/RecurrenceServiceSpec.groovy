package taskboard

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import java.time.LocalDate

class RecurrenceServiceSpec extends Specification implements ServiceUnitTest<RecurrenceService> {

    LocalDate from = LocalDate.of(2026, 1, 7) // a Wednesday

    void "DAILY with interval 1 adds one day"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(1)
    }

    void "DAILY with interval 3 adds three days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.DAILY, interval: 3)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(3)
    }

    void "WEEKLY with interval 1 adds seven days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKLY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(7)
    }

    void "WEEKLY with interval 2 adds fourteen days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKLY, interval: 2)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(14)
    }

    void "MONTHLY with interval 1 adds one month"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.MONTHLY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusMonths(1)
    }

    void "MONTHLY at month-end clamps to the last valid day of the next month"() {
        given:
        LocalDate endOfJanuary = LocalDate.of(2026, 1, 31)
        def rule = new RecurrenceRule(type: RecurrenceType.MONTHLY, interval: 1)

        expect:
        service.nextDueDate(rule, endOfJanuary) == LocalDate.of(2026, 2, 28)
    }

    void "WEEKDAYS resolves to the nearest matching weekday strictly after fromDate"() {
        given: "from is a Wednesday, and Thursday is the only selected weekday"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "THURSDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(1)
    }

    void "WEEKDAYS matching fromDate's own weekday resolves to next week, not the same day"() {
        given: "from is a Wednesday, and Wednesday is the only selected weekday"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "WEDNESDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(7)
    }

    void "WEEKDAYS with multiple selected days picks the nearest one"() {
        given: "from is a Wednesday, Monday and Friday are selected"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY,FRIDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(2) // the Friday
    }
}
