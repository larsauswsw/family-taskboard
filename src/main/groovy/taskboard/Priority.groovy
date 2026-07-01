package taskboard

enum Priority {
    LOW(1.0d), MEDIUM(1.2d), HIGH(1.5d), CRITICAL(2.0d)
    final double multiplier
    Priority(double m) { this.multiplier = m }
}
