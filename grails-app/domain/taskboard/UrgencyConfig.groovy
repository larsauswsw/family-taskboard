package taskboard

/**
 * Singleton row of configurable urgency-color thresholds, in effective days
 * (see UrgencyService). BootStrap seeds exactly one row on first startup;
 * current() reads it back (falling back to defaults if none exists yet, e.g.
 * in a unit test that never seeds one).
 *
 * redDaysThreshold is not currently used by UrgencyService.colorFor() -- below
 * orangeDaysThreshold is simply "red" (or "darkred" once overdue). Kept here
 * for symmetry / potential future use.
 */
class UrgencyConfig {
    Integer greenDaysThreshold = 14
    Integer yellowDaysThreshold = 7
    Integer orangeDaysThreshold = 3
    Integer redDaysThreshold = 1

    static UrgencyConfig current() {
        UrgencyConfig.first() ?: new UrgencyConfig()
    }
}
