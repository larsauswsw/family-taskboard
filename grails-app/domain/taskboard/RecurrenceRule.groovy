package taskboard

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

    static constraints = {
        type nullable: false
        interval nullable: false, min: 1
        weekdays nullable: true, blank: true, validator: { val, obj ->
            if (obj.type == RecurrenceType.WEEKDAYS && !val?.trim()) {
                return 'weekdays.required'
            }
            true
        }
    }
}
