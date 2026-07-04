package taskboard

import java.time.DayOfWeek

/**
 * A recurrence pattern, shared by every Task in the same series (see
 * Task.recurrenceRule's docblock -- plain reference, no GORM
 * belongsTo/hasMany, same reasoning as Project: a rule is never the
 * "owner" of the tasks that reference it). Never deleted once created --
 * a stopped series sets `active = false` instead, so completed history
 * stays intact and interpretable.
 */
class RecurrenceRule {
    RecurrenceType type
    Integer interval = 1
    /** Comma-separated DayOfWeek names (e.g. "MONDAY,THURSDAY"), only
     *  meaningful when type == WEEKDAYS; parsed by RecurrenceService. */
    String weekdays
    boolean active = true

    static mapping = {
        interval column: 'interval_count' // "interval" is a reserved word in H2 (used by the test profile)
    }

    static constraints = {
        type nullable: false
        interval nullable: false, min: 1
        weekdays nullable: true, blank: true, validator: { val, obj ->
            if (obj.type == RecurrenceType.WEEKDAYS) {
                if (!val?.trim()) {
                    return 'weekdays.required'
                }
                // Validate that each comma-separated token is a valid DayOfWeek name.
                // Use split with limit -1 to preserve trailing empty tokens (e.g., "MONDAY," -> ["MONDAY", ""]).
                def tokens = val.split(',', -1)
                for (String token : tokens) {
                    String trimmed = token.trim()
                    if (!trimmed) {
                        return 'weekdays.invalid'
                    }
                    try {
                        DayOfWeek.valueOf(trimmed)
                    } catch (IllegalArgumentException e) {
                        return 'weekdays.invalid'
                    }
                }
            }
            true
        }
    }
}
