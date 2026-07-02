package taskboard

/**
 * multiplier scales down "days until due" in UrgencyService, so a higher-priority
 * task reaches each color band sooner than a lower-priority one with the same due date.
 */
enum Priority {
    LOW(1.0d), MEDIUM(1.2d), HIGH(1.5d), CRITICAL(2.0d)
    final double multiplier
    Priority(double m) { this.multiplier = m }
}
