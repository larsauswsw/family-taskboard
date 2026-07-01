package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UrgencyService {

    double effectiveDays(Task task, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, task.dueDate)
        days / task.priority.multiplier
    }

    String colorFor(Task task, LocalDate today, UrgencyConfig cfg = UrgencyConfig.current()) {
        long rawDays = ChronoUnit.DAYS.between(today, task.dueDate)
        if (rawDays < 0) return 'darkred'
        double eff = effectiveDays(task, today)
        if (eff > cfg.greenDaysThreshold) return 'green'
        if (eff > cfg.yellowDaysThreshold) return 'yellow'
        if (eff >= cfg.orangeDaysThreshold) return 'orange'
        return 'red'
    }
}
