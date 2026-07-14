package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate

/** Shared by both task-creation paths: the web UI's quick-add and the REST quick-add endpoint. */
@Transactional
class TaskService {

    PushService pushService
    DateParsingService dateParsingService
    RecurrenceService recurrenceService

    /** Used identically by TaskController.quickAdd() and ApiTaskController.quick().
     *  If params.dueDate is absent/null, params.title is scanned for a recognized
     *  German date phrase (see DateParsingService); an explicit dueDate always
     *  wins and skips parsing entirely, leaving the title untouched. A null/blank
     *  title skips parsing too (DateParsingService.parse() would NPE on a null
     *  argument) and falls through to Task's own `blank: false` validation, same
     *  as before this feature existed -- ApiTaskController already rejects an
     *  empty title with 422 before calling here, but TaskController.quickAdd()
     *  does not pre-validate, so this guard is this method's own responsibility. */
    Task createTask(Map params, User creator) {
        LocalDate dueDate
        String title = params.title
        if (params.dueDate || !params.title?.trim()) {
            dueDate = params.dueDate ?: LocalDate.now()
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
     *  device/tab. Callers re-render the current list either way. If the completed
     *  task has an active recurrence rule, spawns the next occurrence with its due
     *  date computed from the PREVIOUS due date (not today), so a late completion
     *  doesn't shift the series -- see design spec §3. Both saves flush explicitly
     *  (same as ProjectService.delete()) because GORM integration tests run under
     *  FlushMode.COMMIT: without an explicit flush, a caller querying by status
     *  (e.g. Task.findByTitleAndStatus) right after complete() would still see the
     *  pre-update DB row instead of this method's changes. Spawning is guarded by
     *  wasAlreadyDone: completing never deactivates the recurrence rule, so the same
     *  already-DONE task being completed again (e.g. a stale HTMX card double-clicked
     *  before the swap, or two tabs/devices racing) must be a no-op rather than
     *  spawning a second next occurrence. */
    Task complete(Long id) {
        def t = Task.get(id)
        if (!t) return null
        boolean wasAlreadyDone = t.status == TaskStatus.DONE
        t.status = TaskStatus.DONE
        t.save(flush: true, failOnError: true)
        if (t.createdBy) {
            pushService.sendToUser(t.createdBy, "Task erledigt",
                "'${t.title}' wurde als erledigt markiert")
        }
        if (!wasAlreadyDone && t.recurrenceRule?.active) {
            def next = new Task(
                title: t.title,
                dueDate: recurrenceService.nextDueDate(t.recurrenceRule, t.dueDate),
                priority: t.priority,
                assignedTo: t.assignedTo,
                project: t.project,
                recurrenceRule: t.recurrenceRule,
                createdBy: t.createdBy
            )
            next.save(flush: true, failOnError: true)
        }
        t
    }

    /** Selects assigned, not-yet-done tasks whose due date falls within the assignee's
     *  notifyDaysBefore window and that haven't already been notified about today
     *  (lastNotifiedAt is compared by calendar date so the hourly job can run repeatedly
     *  per day without re-sending). Skips assignees who opted out via notifyOnDueDate. */
    List<Task> tasksNeedingReminder(LocalDate today) {
        Task.findAllByStatusNotEqual(TaskStatus.DONE).findAll { Task t ->
            if (!t.assignedTo || !t.assignedTo.notifyOnDueDate) return false
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

    /** Same defensive pattern as assignTask()/assignProject(). */
    Task updateDueDate(Long taskId, LocalDate dueDate) {
        def t = Task.get(taskId)
        if (!t) return null
        t.dueDate = dueDate
        t.save(failOnError: true)
        t
    }

    /** Same defensive pattern as assignTask()/assignProject()/updateDueDate(). */
    Task updatePriority(Long taskId, Priority priority) {
        def t = Task.get(taskId)
        if (!t) return null
        t.priority = priority
        t.save(failOnError: true)
        t
    }

    /** Creates a brand-new RecurrenceRule for the given task -- always a fresh
     *  row, never reviving a previous, now-stopped rule (see design spec §4).
     *  Returns null for an unknown task id or invalid rule input (interval < 1,
     *  or type WEEKDAYS with no weekdays given) -- same validation pattern as
     *  ProjectService.create()/update(), no exception thrown either way. */
    Task setRecurrence(Long taskId, RecurrenceType type, Integer interval, String weekdays) {
        def t = Task.get(taskId)
        if (!t) return null
        def rule = new RecurrenceRule(type: type, interval: interval ?: 1, weekdays: weekdays)
        if (!rule.save()) return null
        t.recurrenceRule = rule
        t.save(failOnError: true)
        t
    }

    /** Returns null (rather than throwing) for an unknown task id, same
     *  defensive pattern as complete()/assignTask()/assignProject(). A task
     *  with no recurrence rule is left unchanged (idempotent no-op). */
    Task stopRecurrence(Long taskId) {
        def t = Task.get(taskId)
        if (!t) return null
        if (t.recurrenceRule) {
            t.recurrenceRule.active = false
            t.recurrenceRule.save(failOnError: true)
        }
        t
    }
}
