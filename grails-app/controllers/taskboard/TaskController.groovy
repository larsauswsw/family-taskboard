package taskboard

import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate

/**
 * Web UI for the task list. No @Secured annotation: this project has no
 * spring-security-core plugin, so authentication is enforced entirely by
 * SecurityConfig's anyRequest().authenticated() rule (see SecurityConfig.groovy),
 * which already covers everything under /task/**.
 *
 * quickAdd() and complete() are called via HTMX and re-render the "list"
 * template fragment, which HTMX swaps into #task-list -- no page reload.
 */
class TaskController {

    TaskService taskService
    UrgencyService urgencyService

    /** There is no springSecurityService bean here; UserDetailsServiceImpl
     *  returns User instances directly, so the principal already IS a User. */
    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def index() {
        [tasks: taskService.openTasksSorted(), urgencyService: urgencyService,
         today: LocalDate.now(), users: User.list(), projects: Project.list()]
    }

    def quickAdd() {
        User creator = currentUser()
        taskService.createTask([
            title: params.title,
            dueDate: params.dueDate ? LocalDate.parse(params.dueDate) : LocalDate.now(),
            priority: params.priority ? Priority.valueOf(params.priority) : Priority.MEDIUM
        ], creator)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list()]
    }

    def complete(Long id) {
        taskService.complete(id)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list()]
    }

    /** Reachable from the assignee <select> in _card.gsp; params.assignedTo is a User
     *  id, or blank to unassign. This is the only place TaskService.assignTask is
     *  called from -- without it the assignment-notification trigger and the
     *  due-date reminder job (which only selects assigned tasks) would be unreachable. */
    def assign(Long id) {
        User assignee = params.assignedTo ? User.get(params.assignedTo as Long) : null
        taskService.assignTask(id, assignee, currentUser())
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list()]
    }
}
