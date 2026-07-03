package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate

/** Shared by both task-creation paths: the web UI's quick-add and the REST quick-add endpoint. */
@Transactional
class TaskService {

    PushService pushService
    DateParsingService dateParsingService

    /** Used identically by TaskController.quickAdd() and ApiTaskController.quick().
     *  If params.dueDate is absent/null, params.title is scanned for a recognized
     *  German date phrase (see DateParsingService); an explicit dueDate always
     *  wins and skips parsing entirely, leaving the title untouched. */
    Task createTask(Map params, User creator) {
        LocalDate dueDate
        String title = params.title
        if (params.dueDate) {
            dueDate = params.dueDate
        } else {
            def parsed = dateParsingService.parse(params.title as String, LocalDate.now())
            dueDate = parsed.date ?: LocalDate.now()
            title = parsed.title
        }
        def task = new Task(
            title: title,
            dueDate: dueDate,
            priority: params.priority ?: Priority.MEDIUM,
            description: params.description,
            assignedTo: params.assignedTo,
            project: params.project,
            createdBy: creator
        )
        task.save(failOnError: true)
        task
    }

    List<Task> openTasksSorted() {
        Task.findAllByStatusNotEqual(TaskStatus.DONE, [sort: 'dueDate', order: 'asc'])
    }

    /** Tasks in a specific project, same OPEN/IN_PROGRESS-only + dueDate-ascending
     *  behavior as openTasksSorted(). */
    List<Task> tasksForProject(Project project) {
        Task.findAllByStatusNotEqualAndProject(TaskStatus.DONE, project, [sort: 'dueDate', order: 'asc'])
    }

    /** Tasks with no project assigned -- the "Kein Projekt" filter pill. */
    List<Task> tasksWithoutProject() {
        Task.findAllByStatusNotEqualAndProjectIsNull(TaskStatus.DONE, [sort: 'dueDate', order: 'asc'])
    }

    /** Returns null (rather than throwing) for an id that no longer exists -- e.g. a
     *  stale HTMX card still on screen after the task was completed from another
     *  device/tab. Callers re-render the current list either way. */
    Task complete(Long id) {
        def t = Task.get(id)
        if (!t) return null
        t.status = TaskStatus.DONE
        t.save(failOnError: true)
        if (t.createdBy) {
            pushService.sendToUser(t.createdBy, "Task erledigt",
                "'${t.title}' wurde als erledigt markiert")
        }
        t
    }

    /** Selects assigned, not-yet-done tasks whose due date falls within the assignee's
     *  notifyDaysBefore window and that haven't already been notified about today
     *  (lastNotifiedAt is compared by calendar date so the hourly job can run repeatedly
     *  per day without re-sending). */
    List<Task> tasksNeedingReminder(LocalDate today) {
        Task.findAllByStatusNotEqual(TaskStatus.DONE).findAll { Task t ->
            if (!t.assignedTo) return false
            long daysOut = java.time.temporal.ChronoUnit.DAYS.between(today, t.dueDate)
            boolean inWindow = daysOut >= 0 && daysOut <= (t.assignedTo.notifyDaysBefore ?: 1)
            boolean notNotifiedToday = t.lastNotifiedAt == null ||
                t.lastNotifiedAt.toLocalDate().isBefore(today)
            inWindow && notNotifiedToday
        }
    }

    void sendDueReminders(LocalDate today) {
        tasksNeedingReminder(today).each { Task t ->
            pushService.sendToUser(t.assignedTo, "Task fällig",
                "${t.title} ist am ${t.dueDate} fällig")
            t.lastNotifiedAt = java.time.LocalDateTime.now()
            t.save(failOnError: true)
        }
    }

    Task assignTask(Long taskId, User assignee, User actor) {
        def t = Task.get(taskId)
        if (!t) return null
        t.assignedTo = assignee
        t.save(failOnError: true)
        if (assignee && assignee.id != actor?.id) {
            pushService.sendToUser(assignee, "Neuer Task",
                "${actor?.displayName} hat dir '${t.title}' zugewiesen")
        }
        t
    }

    /** Returns null (rather than throwing) for an id that no longer exists,
     *  same defensive pattern as complete()/assignTask(). */
    Task assignProject(Long taskId, Project project) {
        def t = Task.get(taskId)
        if (!t) return null
        t.project = project
        t.save(failOnError: true)
        t
    }
}
