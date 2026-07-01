package taskboard

import java.time.LocalDate
import java.time.LocalDateTime

class Task {
    String title
    LocalDate dueDate
    Priority priority
    TaskStatus status = TaskStatus.OPEN
    String description
    User assignedTo
    User createdBy
    LocalDateTime lastNotifiedAt
    Date dateCreated
    Date lastUpdated

    static constraints = {
        title blank: false, nullable: false
        dueDate nullable: false
        priority nullable: false
        description nullable: true
        assignedTo nullable: true
        createdBy nullable: true
        lastNotifiedAt nullable: true
    }
}
