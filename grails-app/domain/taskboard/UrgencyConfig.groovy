package taskboard

class UrgencyConfig {
    Integer greenDaysThreshold = 14
    Integer yellowDaysThreshold = 7
    Integer orangeDaysThreshold = 3
    Integer redDaysThreshold = 1

    static UrgencyConfig current() {
        UrgencyConfig.first() ?: new UrgencyConfig()
    }
}
