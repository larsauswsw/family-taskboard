package taskboard

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class RecurrenceRuleSpec extends Specification implements DomainUnitTest<RecurrenceRule> {

    void "a DAILY rule with a positive interval is valid"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1).validate()
    }

    void "interval must be at least 1"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.DAILY, interval: 0).validate(['interval'])
    }

    void "type is required"() {
        expect:
        !new RecurrenceRule(type: null, interval: 1).validate(['type'])
    }

    void "WEEKDAYS type requires at least one weekday"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "").validate(['weekdays'])
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: null).validate(['weekdays'])
        new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY").validate(['weekdays'])
    }

    void "non-WEEKDAYS types don't require weekdays"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1, weekdays: null).validate(['weekdays'])
    }

    void "active defaults to true"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1).active
    }

    void "WEEKDAYS type with unparseable token is invalid"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "Someday").validate(['weekdays'])
    }

    void "WEEKDAYS type with trailing comma producing empty token is invalid"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY,").validate(['weekdays'])
    }

    void "WEEKDAYS type with valid comma-separated tokens is valid"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY,THURSDAY").validate(['weekdays'])
    }
}
