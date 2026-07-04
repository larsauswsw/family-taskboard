package taskboard

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Computes the next due date for a recurring task series (see design spec
 * docs/superpowers/specs/2026-07-03-family-taskboard-recurring-tasks-design.md
 * §3). Deterministic: takes `fromDate` as a parameter instead of calling
 * LocalDate.now() internally, same pattern as
 * UrgencyService.colorFor(task, today) and DateParsingService.parse(title, today).
 *
 * `fromDate` is always the PREVIOUS due date, never the date the task was
 * actually completed on -- so a series completed late doesn't drift.
 */
class RecurrenceService {

    LocalDate nextDueDate(RecurrenceRule rule, LocalDate fromDate) {
        switch (rule.type) {
            case RecurrenceType.DAILY:
                return fromDate.plusDays(rule.interval)
            case RecurrenceType.WEEKLY:
                return fromDate.plusWeeks(rule.interval)
            case RecurrenceType.MONTHLY:
                return fromDate.plusMonths(rule.interval)
            case RecurrenceType.WEEKDAYS:
                return nextMatchingWeekday(fromDate, parseWeekdays(rule.weekdays))
            default:
                throw new IllegalStateException("Unknown recurrence type: ${rule.type}")
        }
    }

    private static List<DayOfWeek> parseWeekdays(String csv) {
        csv.split(',').collect { DayOfWeek.valueOf(it.trim()) }
    }

    /** Smallest date strictly after fromDate whose weekday is in targets --
     *  checking offsets 1..7 always finds a match since targets is a
     *  non-empty subset of the 7 weekdays. */
    private static LocalDate nextMatchingWeekday(LocalDate fromDate, List<DayOfWeek> targets) {
        (1..7).collect { fromDate.plusDays(it) }.find { it.dayOfWeek in targets }
    }
}
