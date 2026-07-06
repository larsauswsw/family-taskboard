package taskboard

/**
 * multiplier scales down "days until due" in UrgencyService, so a higher-priority
 * task reaches each color band sooner than a lower-priority one with the same due date.
 * germanLabel is the display string shown on task cards (never the raw enum name).
 */
enum Priority {
    LOW(1.0d, "Niedrig"), MEDIUM(1.2d, "Mittel"),
    HIGH(1.5d, "Hoch"), CRITICAL(2.0d, "Kritisch")
    final double multiplier
    final String germanLabel
    Priority(double m, String label) { this.multiplier = m; this.germanLabel = label }
}
