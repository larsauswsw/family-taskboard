package taskboard

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A single task. Urgency (its display color) is not stored here -- it's
 * derived on the fly from dueDate + priority by UrgencyService, so changing
 * UrgencyConfig's thresholds retroactively affects every task's color.
 */
class Task {
    String title
    LocalDate dueDate
    Priority priority
    TaskStatus status = TaskStatus.OPEN
    String description
    User assignedTo
    User createdBy
    /** Optional grouping label (Phase 2). Plain reference, not GORM belongsTo --
     *  see Project's docblock. */
    Project project
    /** Set by the (not yet built) reminder scheduler to avoid re-notifying. */
    LocalDateTime lastNotifiedAt
    Date dateCreated
    Date lastUpdated

    static constraints = {
        title blank: false, nullable: false
        dueDate nullable: false
        priority nullable: false
        description nullable: true
        assignedTo nullable: true
        // Nullable only because every caller (TaskController, ApiTaskController)
        // is trusted to always pass the authenticated user explicitly -- GORM
        // itself does not enforce that a task has a creator.
        createdBy nullable: true
        project nullable: true
        lastNotifiedAt nullable: true
    }
}
