package taskboard

import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate

/**
 * Web UI for the task list. No @Secured annotation: this project has no
 * spring-security-core plugin, so authentication is enforced entirely by
 * SecurityConfig's anyRequest().authenticated() rule (see SecurityConfig.groovy),
 * which already covers everything under /task/**.
 *
 * quickAdd(), complete(), assign() and assignProject() are called via HTMX and
 * re-render the "list" template fragment, which HTMX swaps into #task-list --
 * no page reload. That fragment now includes the project filter pills, so
 * every one of those actions must supply `projects` (and `selectedProject`,
 * always null for them -- they don't change the active filter, matching the
 * existing behavior where completing/assigning a task always returns to the
 * unfiltered "Alle" view).
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
         today: LocalDate.now(), users: User.list(), projects: Project.list(),
         selectedProject: null]
    }

    def quickAdd() {
        User creator = currentUser()
        taskService.createTask([
            title: params.title,
            dueDate: params.dueDate ? LocalDate.parse(params.dueDate) : null,
            priority: params.priority ? Priority.valueOf(params.priority) : Priority.MEDIUM,
            project: params.project ? Project.get(params.project as Long) : null
        ], creator)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }

    def complete(Long id) {
        taskService.complete(id)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }

    /** Reachable from the assignee <select> in _card.gsp; params.assignedTo is a User
     *  id, or blank to unassign. This is the only place TaskService.assignTask is
     *  called from -- without it the assignment-notification trigger and the
     *  due-date reminder job (which only selects assigned tasks) would be unreachable. */
    def assign(Long id) {
        User assignee = params.assignedTo ? User.get(params.assignedTo as Long) : null
        taskService.assignTask(id, assignee, currentUser())
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }

    /** Reachable from the project <select> in _card.gsp; params.project is a Project
     *  id, or blank to remove the task from any project. */
    def assignProject(Long id) {
        Project project = params.project ? Project.get(params.project as Long) : null
        taskService.assignProject(id, project)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }

    /** Reachable from the project filter pills above the task list. params.project is
     *  a Project id, the literal "none" for the "Kein Projekt" pill, or absent/blank
     *  for "Alle". An id that no longer resolves to a Project falls back to the
     *  unfiltered list rather than erroring -- e.g. a stale pill left over after
     *  another device deleted that project. */
    def list() {
        List<Task> tasks
        String selectedProject = params.project
        if (!selectedProject) {
            tasks = taskService.openTasksSorted()
            selectedProject = null
        } else if (selectedProject == 'none') {
            tasks = taskService.tasksWithoutProject()
        } else if (selectedProject.isLong()) {
            Project project = Project.get(selectedProject as Long)
            if (project) {
                tasks = taskService.tasksForProject(project)
            } else {
                tasks = taskService.openTasksSorted()
                selectedProject = null
            }
        } else {
            // Malformed value (e.g. a hand-typed ?project=abc) -- not reachable from
            // the UI, which only ever emits a numeric id or "none", but falls back to
            // the unfiltered list rather than a NumberFormatException/500, same as an
            // id that no longer resolves to a Project.
            tasks = taskService.openTasksSorted()
            selectedProject = null
        }
        render template: 'list', model: [tasks: tasks, urgencyService: urgencyService,
            today: LocalDate.now(), users: User.list(), projects: Project.list(),
            selectedProject: selectedProject]
    }
}
