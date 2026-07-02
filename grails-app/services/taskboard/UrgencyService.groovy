package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Derives a task's display color from its due date and priority. See UrgencyConfig for the thresholds. */
class UrgencyService {

    /** Days until due, divided by the priority multiplier -- higher priority reaches each color band sooner. */
    double effectiveDays(Task task, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, task.dueDate)
        days / task.priority.multiplier
    }

    /** One of 'green', 'yellow', 'orange', 'red', 'darkred' (darkred iff already overdue). */
    String colorFor(Task task, LocalDate today, UrgencyConfig cfg = UrgencyConfig.current()) {
        long rawDays = ChronoUnit.DAYS.between(today, task.dueDate)
        if (rawDays < 0) return 'darkred'
        double eff = effectiveDays(task, today)
        // Thresholds are inclusive lower bounds (>=) consistently across all bands,
        // e.g. eff exactly at orangeDaysThreshold still counts as orange, not red.
        if (eff >= cfg.greenDaysThreshold) return 'green'
        if (eff >= cfg.yellowDaysThreshold) return 'yellow'
        if (eff >= cfg.orangeDaysThreshold) return 'orange'
        return 'red'
    }
}
