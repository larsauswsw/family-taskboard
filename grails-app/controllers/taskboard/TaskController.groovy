package taskboard

import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate

class TaskController {

    TaskService taskService
    UrgencyService urgencyService

    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def index() {
        [tasks: taskService.openTasksSorted(), urgencyService: urgencyService,
         today: LocalDate.now(), users: User.list()]
    }

    def quickAdd() {
        User creator = currentUser()
        taskService.createTask([
            title: params.title,
            dueDate: params.dueDate ? LocalDate.parse(params.dueDate) : LocalDate.now(),
            priority: params.priority ? Priority.valueOf(params.priority) : Priority.MEDIUM
        ], creator)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now()]
    }

    def complete(Long id) {
        taskService.complete(id)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now()]
    }
}
