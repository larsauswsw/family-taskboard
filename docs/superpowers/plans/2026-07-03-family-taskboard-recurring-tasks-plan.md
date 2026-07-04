# Recurring Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a task be marked as recurring (daily/weekly/monthly with an interval, or specific weekdays). When a recurring task is completed, automatically create the next occurrence with a correctly computed due date. Let the user configure or stop a recurrence directly from the task card.

**Architecture:** A new `RecurrenceRule` domain class (plain reference from `Task`, never GORM `belongsTo`/`hasMany` — same reasoning as `Project`) holds the pattern and an `active` flag. A new, deterministic `RecurrenceService.nextDueDate(rule, fromDate)` (same "no `LocalDate.now()` inside" pattern as `UrgencyService`/`DateParsingService`) computes the next date from the *previous* due date. `TaskService.complete()` — the existing single choke point for marking a task done — is extended to spawn the next occurrence when the completed task has an active rule. Configuration lives inline on the task card, following the same collapsible-`<details>` + HTMX-swap pattern already used for project management.

**Tech Stack:** Grails 7.1.1, GORM, Spock (unit + `@Integration`), HTMX 2.0.3 — no new dependencies.

## Global Constraints

- Grails 7.1.1 / Java 21 / Spock — same stack as Phase 1/2 and date-parsing, no version changes.
- Recurrence types: `DAILY`, `WEEKLY`, `MONTHLY` (each with an `interval >= 1`, default 1), `WEEKDAYS` (a set of weekdays, `interval` unused).
- The next due date is always computed from the **previous due date**, never from the date the task was actually completed on.
- `RecurrenceRule` is never deleted — a stopped series sets `active = false` and keeps the row (and its task history) intact.
- `setRecurrence` always creates a brand-new `RecurrenceRule`, even if the task previously had one that's now stopped — there is no reviving an inactive rule.
- All new task-lookup service methods follow the established defensive pattern: return `null` for an unknown task id rather than throwing (same as `complete()`/`assignTask()`/`assignProject()`).
- All new domain/service validation follows the established pattern: `save()` without `failOnError` returns `null` on invalid input, which callers translate into a controller-level error message (same as `ProjectService.create()`/`update()`).
- Recurrence is configured *after* a task is created, from the task card — quick-add is not touched by this feature.

---

### Task 1: `RecurrenceType` enum + `RecurrenceRule` domain + `RecurrenceService`

**Files:**
- Create: `grails-app/domain/taskboard/RecurrenceType.groovy`
- Create: `grails-app/domain/taskboard/RecurrenceRule.groovy`
- Create: `grails-app/services/taskboard/RecurrenceService.groovy`
- Test: `src/test/groovy/taskboard/RecurrenceRuleSpec.groovy`
- Test: `src/test/groovy/taskboard/RecurrenceServiceSpec.groovy`

**Interfaces:**
- Consumes: nothing (foundational, no DB writes exercised by these tests).
- Produces: `RecurrenceType` enum (`DAILY`, `WEEKLY`, `MONTHLY`, `WEEKDAYS`). `RecurrenceRule { RecurrenceType type; Integer interval; String weekdays; boolean active }` (GORM domain class). `RecurrenceService.nextDueDate(RecurrenceRule rule, LocalDate fromDate) -> LocalDate`.

- [ ] **Step 1: Write the failing domain unit test**

Create `src/test/groovy/taskboard/RecurrenceRuleSpec.groovy`:

```groovy
package taskboard

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class RecurrenceRuleSpec extends Specification implements DomainUnitTest<RecurrenceRule> {

    void "a DAILY rule with a positive interval is valid"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1).validate()
    }

    void "interval must be at least 1"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.DAILY, interval: 0).validate(['interval'])
    }

    void "type is required"() {
        expect:
        !new RecurrenceRule(type: null, interval: 1).validate(['type'])
    }

    void "WEEKDAYS type requires at least one weekday"() {
        expect:
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "").validate(['weekdays'])
        !new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: null).validate(['weekdays'])
        new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY").validate(['weekdays'])
    }

    void "non-WEEKDAYS types don't require weekdays"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1, weekdays: null).validate(['weekdays'])
    }

    void "active defaults to true"() {
        expect:
        new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1).active
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "taskboard.RecurrenceRuleSpec"`
Expected: compilation FAILURE — `RecurrenceType` and `RecurrenceRule` don't exist yet.

- [ ] **Step 3: Implement `RecurrenceType.groovy`**

Create `grails-app/domain/taskboard/RecurrenceType.groovy`:

```groovy
package taskboard

enum RecurrenceType {
    DAILY, WEEKLY, MONTHLY, WEEKDAYS
}
```

- [ ] **Step 4: Implement `RecurrenceRule.groovy`**

Create `grails-app/domain/taskboard/RecurrenceRule.groovy`:

```groovy
package taskboard

/**
 * A recurrence pattern, shared by every Task in the same series (see
 * Task.recurrenceRule's docblock -- plain reference, no GORM
 * belongsTo/hasMany, same reasoning as Project: a rule is never the
 * "owner" of the tasks that reference it). Never deleted once created --
 * a stopped series sets `active = false` instead, so completed history
 * stays intact and interpretable.
 */
class RecurrenceRule {
    RecurrenceType type
    Integer interval = 1
    /** Comma-separated DayOfWeek names (e.g. "MONDAY,THURSDAY"), only
     *  meaningful when type == WEEKDAYS; parsed by RecurrenceService. */
    String weekdays
    boolean active = true

    static constraints = {
        type nullable: false
        interval nullable: false, min: 1
        weekdays nullable: true, blank: true, validator: { val, obj ->
            if (obj.type == RecurrenceType.WEEKDAYS && !val?.trim()) {
                return 'weekdays.required'
            }
            true
        }
    }
}
```

- [ ] **Step 5: Run the domain test to verify it passes**

Run: `./gradlew test --tests "taskboard.RecurrenceRuleSpec"`
Expected: PASS (6 tests).

- [ ] **Step 6: Write the failing service unit test**

Create `src/test/groovy/taskboard/RecurrenceServiceSpec.groovy`:

```groovy
package taskboard

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import java.time.LocalDate

class RecurrenceServiceSpec extends Specification implements ServiceUnitTest<RecurrenceService> {

    LocalDate from = LocalDate.of(2026, 1, 7) // a Wednesday

    void "DAILY with interval 1 adds one day"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.DAILY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(1)
    }

    void "DAILY with interval 3 adds three days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.DAILY, interval: 3)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(3)
    }

    void "WEEKLY with interval 1 adds seven days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKLY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(7)
    }

    void "WEEKLY with interval 2 adds fourteen days"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKLY, interval: 2)

        expect:
        service.nextDueDate(rule, from) == from.plusDays(14)
    }

    void "MONTHLY with interval 1 adds one month"() {
        given:
        def rule = new RecurrenceRule(type: RecurrenceType.MONTHLY, interval: 1)

        expect:
        service.nextDueDate(rule, from) == from.plusMonths(1)
    }

    void "MONTHLY at month-end clamps to the last valid day of the next month"() {
        given:
        LocalDate endOfJanuary = LocalDate.of(2026, 1, 31)
        def rule = new RecurrenceRule(type: RecurrenceType.MONTHLY, interval: 1)

        expect:
        service.nextDueDate(rule, endOfJanuary) == LocalDate.of(2026, 2, 28)
    }

    void "WEEKDAYS resolves to the nearest matching weekday strictly after fromDate"() {
        given: "from is a Wednesday, and Thursday is the only selected weekday"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "THURSDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(1)
    }

    void "WEEKDAYS matching fromDate's own weekday resolves to next week, not the same day"() {
        given: "from is a Wednesday, and Wednesday is the only selected weekday"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "WEDNESDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(7)
    }

    void "WEEKDAYS with multiple selected days picks the nearest one"() {
        given: "from is a Wednesday, Monday and Friday are selected"
        def rule = new RecurrenceRule(type: RecurrenceType.WEEKDAYS, interval: 1, weekdays: "MONDAY,FRIDAY")

        expect:
        service.nextDueDate(rule, from) == from.plusDays(2) // the Friday
    }
}
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `./gradlew test --tests "taskboard.RecurrenceServiceSpec"`
Expected: compilation FAILURE — `RecurrenceService` doesn't exist yet.

- [ ] **Step 8: Implement `RecurrenceService.groovy`**

Create `grails-app/services/taskboard/RecurrenceService.groovy`:

```groovy
package taskboard

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Computes the next due date for a recurring task series (see design spec
 * docs/superpowers/specs/2026-07-03-family-taskboard-recurring-tasks-design.md
 * §3). Deterministic: takes `fromDate` as a parameter instead of calling
 * LocalDate.now() internally, same pattern as
 * UrgencyService.colorFor(task, today) and DateParsingService.parse(title, today).
 *
 * `fromDate` is always the PREVIOUS due date, never the date the task was
 * actually completed on -- so a series completed late doesn't drift.
 */
class RecurrenceService {

    LocalDate nextDueDate(RecurrenceRule rule, LocalDate fromDate) {
        switch (rule.type) {
            case RecurrenceType.DAILY:
                return fromDate.plusDays(rule.interval)
            case RecurrenceType.WEEKLY:
                return fromDate.plusWeeks(rule.interval)
            case RecurrenceType.MONTHLY:
                return fromDate.plusMonths(rule.interval)
            case RecurrenceType.WEEKDAYS:
                return nextMatchingWeekday(fromDate, parseWeekdays(rule.weekdays))
            default:
                throw new IllegalStateException("Unknown recurrence type: ${rule.type}")
        }
    }

    private static List<DayOfWeek> parseWeekdays(String csv) {
        csv.split(',').collect { DayOfWeek.valueOf(it.trim()) }
    }

    /** Smallest date strictly after fromDate whose weekday is in targets --
     *  checking offsets 1..7 always finds a match since targets is a
     *  non-empty subset of the 7 weekdays. */
    private static LocalDate nextMatchingWeekday(LocalDate fromDate, List<DayOfWeek> targets) {
        (1..7).collect { fromDate.plusDays(it) }.find { it.dayOfWeek in targets }
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew test --tests "taskboard.RecurrenceRuleSpec" --tests "taskboard.RecurrenceServiceSpec"`
Expected: PASS (6 + 9 = 15 tests).

- [ ] **Step 10: Run the full unit test suite to confirm nothing broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all unit specs pass, including pre-existing `TaskSpec`, `UserSpec`, `UrgencyServiceSpec`, `PushServicePayloadSpec`, `ProjectSpec`, `DateParsingServiceSpec`.

- [ ] **Step 11: Commit**

```bash
git add grails-app/domain/taskboard/RecurrenceType.groovy grails-app/domain/taskboard/RecurrenceRule.groovy grails-app/services/taskboard/RecurrenceService.groovy src/test/groovy/taskboard/RecurrenceRuleSpec.groovy src/test/groovy/taskboard/RecurrenceServiceSpec.groovy
git commit -m "feat: add RecurrenceRule domain and RecurrenceService"
```

---

### Task 2: Wire recurrence into `Task` and `TaskService`

**Files:**
- Modify: `grails-app/domain/taskboard/Task.groovy`
- Modify: `grails-app/services/taskboard/TaskService.groovy`
- Modify: `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `RecurrenceRule` domain class, `RecurrenceService.nextDueDate(rule, fromDate)` (Task 1).
- Produces: `Task.recurrenceRule` field (nullable). `TaskService.complete(Long id)` — unchanged signature, but now spawns the next occurrence when the completed task has an active rule. `TaskService.setRecurrence(Long taskId, RecurrenceType type, Integer interval, String weekdays) -> Task` (null on unknown task id or invalid rule). `TaskService.stopRecurrence(Long taskId) -> Task` (null on unknown task id; no-op if the task has no rule).

- [ ] **Step 1: Write the failing integration tests**

Append these test methods to `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`, just before the final closing `}` of the class:

```groovy
    void "complete on a task with an active recurrence rule creates the next occurrence"() {
        given:
        def u = new User(username: "rec-u1", password: "p",
            displayName: "U", apiToken: "recu1").save(flush: true)
        LocalDate original = LocalDate.now().plusDays(5)
        def t = taskService.createTask([title: "Müll rausbringen",
            dueDate: original, priority: Priority.LOW], u)
        taskService.setRecurrence(t.id, RecurrenceType.WEEKLY, 1, null)

        when:
        taskService.complete(t.id)
        def next = Task.findByTitleAndStatus("Müll rausbringen", TaskStatus.OPEN)

        then:
        next != null
        next.dueDate == original.plusWeeks(1)
        next.recurrenceRule.id == Task.get(t.id).recurrenceRule.id
        next.priority == Priority.LOW
    }

    void "complete on a task with a stopped recurrence rule does not create a next occurrence"() {
        given:
        def u = new User(username: "rec-u2", password: "p",
            displayName: "U", apiToken: "recu2").save(flush: true)
        def t = taskService.createTask([title: "Stopped-Series-Task",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)
        taskService.setRecurrence(t.id, RecurrenceType.DAILY, 1, null)
        taskService.stopRecurrence(t.id)

        when:
        taskService.complete(t.id)

        then:
        Task.findAllByTitle("Stopped-Series-Task").size() == 1
    }

    void "complete on a task without a recurrence rule creates no next occurrence"() {
        given:
        def u = new User(username: "rec-u3", password: "p",
            displayName: "U", apiToken: "recu3").save(flush: true)
        def t = taskService.createTask([title: "Plain-Task",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)

        when:
        taskService.complete(t.id)

        then:
        Task.findAllByTitle("Plain-Task").size() == 1
    }

    void "setRecurrence creates a new active rule and links it to the task"() {
        given:
        def u = new User(username: "rec-u4", password: "p",
            displayName: "U", apiToken: "recu4").save(flush: true)
        def t = taskService.createTask([title: "x",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)

        when:
        def result = taskService.setRecurrence(t.id, RecurrenceType.MONTHLY, 2, null)

        then:
        result.recurrenceRule != null
        result.recurrenceRule.type == RecurrenceType.MONTHLY
        result.recurrenceRule.interval == 2
        result.recurrenceRule.active
        Task.get(t.id).recurrenceRule.type == RecurrenceType.MONTHLY
    }

    void "setRecurrence returns null for an unknown task id"() {
        expect:
        taskService.setRecurrence(-1L, RecurrenceType.DAILY, 1, null) == null
    }

    void "setRecurrence rejects an invalid rule and leaves the task without one"() {
        given:
        def u = new User(username: "rec-u5", password: "p",
            displayName: "U", apiToken: "recu5").save(flush: true)
        def t = taskService.createTask([title: "x",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)

        when:
        def result = taskService.setRecurrence(t.id, RecurrenceType.WEEKDAYS, 1, null)

        then:
        result == null
        Task.get(t.id).recurrenceRule == null
    }

    void "stopRecurrence deactivates the rule so complete no longer regenerates"() {
        given:
        def u = new User(username: "rec-u6", password: "p",
            displayName: "U", apiToken: "recu6").save(flush: true)
        def t = taskService.createTask([title: "y",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)
        taskService.setRecurrence(t.id, RecurrenceType.DAILY, 1, null)

        when:
        def result = taskService.stopRecurrence(t.id)

        then:
        !result.recurrenceRule.active
    }

    void "stopRecurrence returns null for an unknown task id"() {
        expect:
        taskService.stopRecurrence(-1L) == null
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: FAILURE — compilation error (`RecurrenceType` unresolved in test context is fine, it exists from Task 1, but `taskService.setRecurrence`/`stopRecurrence` and `Task.recurrenceRule` don't exist yet).

- [ ] **Step 3: Add `recurrenceRule` to `Task.groovy`**

In `grails-app/domain/taskboard/Task.groovy`, add the field just after the existing `project` field:

```groovy
    /** Optional grouping label (Phase 2). Plain reference, not GORM belongsTo --
     *  see Project's docblock. */
    Project project
    /** Optional recurrence pattern. Plain reference, not GORM belongsTo --
     *  same reasoning as Project: a rule is shared by every Task instance in
     *  the same series and is never deleted, only deactivated. See
     *  RecurrenceRule's docblock. */
    RecurrenceRule recurrenceRule
```

And add the corresponding constraint line right after `project nullable: true`:

```groovy
        project nullable: true
        recurrenceRule nullable: true
```

- [ ] **Step 4: Update `TaskService.groovy`**

Replace the full contents of `grails-app/services/taskboard/TaskService.groovy` with:

```groovy
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
     *  doesn't shift the series -- see design spec §3. */
    Task complete(Long id) {
        def t = Task.get(id)
        if (!t) return null
        t.status = TaskStatus.DONE
        t.save(failOnError: true)
        if (t.createdBy) {
            pushService.sendToUser(t.createdBy, "Task erledigt",
                "'${t.title}' wurde als erledigt markiert")
        }
        if (t.recurrenceRule?.active) {
            def next = new Task(
                title: t.title,
                dueDate: recurrenceService.nextDueDate(t.recurrenceRule, t.dueDate),
                priority: t.priority,
                assignedTo: t.assignedTo,
                project: t.project,
                recurrenceRule: t.recurrenceRule,
                createdBy: t.createdBy
            )
            next.save(failOnError: true)
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
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: PASS (21 tests: 13 pre-existing + 8 new).

- [ ] **Step 6: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL — every unit and integration spec passes, including all pre-existing Phase 1/2/date-parsing specs.

- [ ] **Step 7: Commit**

```bash
git add grails-app/domain/taskboard/Task.groovy grails-app/services/taskboard/TaskService.groovy src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy
git commit -m "feat: generate the next occurrence when a recurring task is completed"
```

---

### Task 3: `TaskController` actions + task-card UI

**Files:**
- Modify: `grails-app/controllers/taskboard/TaskController.groovy`
- Modify: `grails-app/views/task/_card.gsp`
- Modify: `grails-app/views/task/index.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Create: `grails-app/assets/javascripts/recurrence.js`
- Test: `src/integration-test/groovy/taskboard/TaskControllerSessionFlowIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `TaskService.setRecurrence(Long, RecurrenceType, Integer, String)`, `TaskService.stopRecurrence(Long)` (Task 2).
- Produces: `POST /task/setRecurrence/<id>` and `POST /task/stopRecurrence/<id>`, both rendering the `list` template like every other card action.

- [ ] **Step 1: Write the failing integration test**

Append this test method to `src/integration-test/groovy/taskboard/TaskControllerSessionFlowIntegrationSpec.groovy`, just before the final closing `}` of the class. This file has no shared login-helper method — every existing test in it inlines the same login sequence (see e.g. `"assigning a task via the card's select..."` above it), so this one does too, for consistency:

```groovy
    void "setting and then stopping a recurrence via HTMX round-trips correctly"() {
        given: "a logged-in session"
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        extractCookies(initial, cookies)

        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookieHeader(cookies))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=changeme&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        login.responseCode
        extractCookies(login, cookies)

        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)

        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Recurring-Flow-Task', 'UTF-8')}" }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)
        Long taskId
        Task.withTransaction { taskId = Task.findByTitle("Recurring-Flow-Task").id }

        when: "setting a weekly recurrence"
        def setRec = new URL("http://localhost:${serverPort}/task/setRecurrence/${taskId}").openConnection()
        setRec.requestMethod = "POST"
        setRec.doOutput = true
        setRec.instanceFollowRedirects = false
        setRec.setRequestProperty("Cookie", cookieHeader(cookies))
        setRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        setRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRec.outputStream.withWriter { it << "type=WEEKLY&interval=1" }
        int setRecStatus = setRec.responseCode
        extractCookies(setRec, cookies)

        then:
        setRecStatus == 200
        Task.withTransaction { Task.get(taskId).recurrenceRule?.active }

        when: "stopping the series"
        def stopRec = new URL("http://localhost:${serverPort}/task/stopRecurrence/${taskId}").openConnection()
        stopRec.requestMethod = "POST"
        stopRec.doOutput = true
        stopRec.instanceFollowRedirects = false
        stopRec.setRequestProperty("Cookie", cookieHeader(cookies))
        stopRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        stopRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        stopRec.outputStream.withWriter { it << "" }
        int stopRecStatus = stopRec.responseCode

        then:
        stopRecStatus == 200
        Task.withTransaction { !Task.get(taskId).recurrenceRule?.active }

        cleanup:
        Task.withTransaction { Task.findAllByTitle("Recurring-Flow-Task")*.delete(flush: true) }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.TaskControllerSessionFlowIntegrationSpec"`
Expected: FAILURE — 404, `TaskController.setRecurrence`/`stopRecurrence` don't exist yet.

- [ ] **Step 3: Add the two new actions to `TaskController.groovy`**

In `grails-app/controllers/taskboard/TaskController.groovy`, add these two actions right after `assignProject(Long id)`:

```groovy
    /** Reachable from the recurrence form in _card.gsp's "🔁 Wiederholung"
     *  details section. params.type is a RecurrenceType name (the <select>
     *  only ever emits a valid one, same trust level as Priority.valueOf in
     *  quickAdd()); params.interval is optional (defaults to 1 in the
     *  service); params.weekday may appear multiple times (one per checked
     *  checkbox), only when type is WEEKDAYS. */
    def setRecurrence(Long id) {
        RecurrenceType type = RecurrenceType.valueOf(params.type)
        Integer interval = params.interval ? params.interval as Integer : null
        String weekdays = params.list('weekday') ? params.list('weekday').join(',') : null
        taskService.setRecurrence(id, type, interval, weekdays)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }

    /** Reachable from the "Serie beenden" button in _card.gsp. */
    def stopRecurrence(Long id) {
        taskService.stopRecurrence(id)
        render template: 'list', model: [tasks: taskService.openTasksSorted(),
            urgencyService: urgencyService, today: LocalDate.now(), users: User.list(),
            projects: Project.list(), selectedProject: null]
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.TaskControllerSessionFlowIntegrationSpec"`
Expected: PASS.

- [ ] **Step 5: Add the recurrence UI to `_card.gsp`**

Replace the full contents of `grails-app/views/task/_card.gsp` with:

```gsp
<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <g:if test="${task.project}">
        <span class="project-chip" style="background-color: ${task.project.color};">${task.project.name}</span>
    </g:if>
    <div class="task-meta">
        <select name="assignedTo" class="assignee-select"
                hx-post="${createLink(action: 'assign', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.assignedTo ? '' : 'selected'}>—</option>
            <g:each in="${users}" var="u">
                <option value="${u.id}" ${task.assignedTo?.id == u.id ? 'selected' : ''}>${u.displayName}</option>
            </g:each>
        </select>
        <select name="project" class="project-select"
                hx-post="${createLink(action: 'assignProject', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.project ? '' : 'selected'}>Kein Projekt</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}" ${task.project?.id == p.id ? 'selected' : ''}>${p.name}</option>
            </g:each>
        </select>
        <span><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority}</span>
        <g:if test="${task.recurrenceRule?.active}">
            <span class="recurrence-badge" title="Wiederholt sich">🔁</span>
        </g:if>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
    <details class="recurrence-details">
        <summary>🔁 Wiederholung</summary>
        <g:if test="${task.recurrenceRule?.active}">
            <p class="recurrence-summary">Wiederholt sich: ${task.recurrenceRule.type}</p>
            <form hx-post="${createLink(action: 'stopRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <button type="submit">Serie beenden</button>
            </form>
        </g:if>
        <g:else>
            <form class="recurrence-form"
                  hx-post="${createLink(action: 'setRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <select name="type" class="recurrence-type-select"
                        onchange="taskboardToggleRecurrenceFields(this)">
                    <option value="DAILY">Täglich</option>
                    <option value="WEEKLY">Wöchentlich</option>
                    <option value="MONTHLY">Monatlich</option>
                    <option value="WEEKDAYS">Wochentage</option>
                </select>
                <span class="recurrence-interval-field">
                    <label>alle <input type="number" name="interval" value="1" min="1"> ×</label>
                </span>
                <span class="recurrence-weekday-fields" hidden>
                    <label><input type="checkbox" name="weekday" value="MONDAY">Mo</label>
                    <label><input type="checkbox" name="weekday" value="TUESDAY">Di</label>
                    <label><input type="checkbox" name="weekday" value="WEDNESDAY">Mi</label>
                    <label><input type="checkbox" name="weekday" value="THURSDAY">Do</label>
                    <label><input type="checkbox" name="weekday" value="FRIDAY">Fr</label>
                    <label><input type="checkbox" name="weekday" value="SATURDAY">Sa</label>
                    <label><input type="checkbox" name="weekday" value="SUNDAY">So</label>
                </span>
                <button type="submit">Übernehmen</button>
            </form>
        </g:else>
    </details>
</div>
```

- [ ] **Step 6: Add the field-toggle script**

Create `grails-app/assets/javascripts/recurrence.js`:

```javascript
function taskboardToggleRecurrenceFields(selectEl) {
    const form = selectEl.closest('form');
    const isWeekdays = selectEl.value === 'WEEKDAYS';
    form.querySelector('.recurrence-interval-field').hidden = isWeekdays;
    form.querySelector('.recurrence-weekday-fields').hidden = !isWeekdays;
}
```

- [ ] **Step 7: Include the new script in `index.gsp`**

In `grails-app/views/task/index.gsp`, add this line right after `<asset:javascript src="push.js"/>`:

```gsp
    <asset:javascript src="recurrence.js"/>
```

- [ ] **Step 8: Add recurrence styles to `taskboard.css`**

Append to `grails-app/assets/stylesheets/taskboard.css`:

```css
.recurrence-badge { margin-left: 4px; }
.recurrence-details { margin-top: 6px; font-size: 0.85em; }
.recurrence-details summary { cursor: pointer; }
.recurrence-form span { margin-right: 8px; }
```

- [ ] **Step 9: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Manually verify via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, then in a browser:
1. Log in as `lars`/`changeme`.
2. Quick-add a task, e.g. "Rasen mähen".
3. On its card, open "🔁 Wiederholung", select "Wöchentlich", leave interval at 1, click "Übernehmen".
4. Confirm the 🔁 badge appears on the card and the details section now shows "Wiederholt sich: WEEKLY" with a "Serie beenden" button.
5. Click "✓" to complete the task — confirm a new card for "Rasen mähen" appears with a due date one week after the original, and it also carries the 🔁 badge.
6. Open the new card's details, click "Serie beenden" — confirm the badge disappears and the details section reverts to the configuration form.
7. Complete this task too — confirm no further occurrence is created.

- [ ] **Step 11: Commit**

```bash
git add grails-app/controllers/taskboard/TaskController.groovy grails-app/views/task/_card.gsp grails-app/views/task/index.gsp grails-app/assets/stylesheets/taskboard.css grails-app/assets/javascripts/recurrence.js src/integration-test/groovy/taskboard/TaskControllerSessionFlowIntegrationSpec.groovy
git commit -m "feat: configure and stop task recurrence from the task card"
```

---

### Task 4: Full end-to-end recurrence flow test

**Files:**
- Create: `src/integration-test/groovy/taskboard/RecurrenceFlowIntegrationSpec.groovy`

**Interfaces:**
- Consumes: everything from Tasks 1-3. No new production code.

- [ ] **Step 1: Write the flow test**

Create `src/integration-test/groovy/taskboard/RecurrenceFlowIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import java.time.LocalDate

/**
 * End-to-end HTTP flow tying the whole recurring-tasks feature together:
 * quick-add a task, set a daily recurrence, complete it, confirm the next
 * occurrence appears with the correct due date and still-active rule, stop
 * the series, complete the new occurrence, and confirm no further task is
 * spawned. Matches the scenario required by the design spec §6.
 */
@Integration
class RecurrenceFlowIntegrationSpec extends Specification {

    @Autowired
    ServletWebServerApplicationContext applicationContext

    Integer getServerPort() {
        applicationContext.webServer.port
    }

    private static String cookieHeader(Map<String, String> cookies) {
        cookies.collect { k, v -> "${k}=${v}" }.join('; ')
    }

    private static Map<String, String> extractCookies(URLConnection conn, Map<String, String> into) {
        conn.headerFields.get('Set-Cookie')?.each { String raw ->
            def pair = raw.split(';')[0].split('=', 2)
            if (pair.length == 2) into[pair[0]] = pair[1]
        }
        into
    }

    private Map<String, String> loggedInCookies() {
        Map<String, String> cookies = [:]
        def initial = new URL("http://localhost:${serverPort}/login/auth").openConnection()
        initial.instanceFollowRedirects = false
        initial.responseCode
        extractCookies(initial, cookies)

        def login = new URL("http://localhost:${serverPort}/login").openConnection()
        login.requestMethod = "POST"
        login.doOutput = true
        login.instanceFollowRedirects = false
        login.setRequestProperty("Cookie", cookieHeader(cookies))
        login.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        String csrfToken = cookies['XSRF-TOKEN']
        String form = "username=lars&password=changeme&_csrf=${URLEncoder.encode(csrfToken, 'UTF-8')}"
        login.outputStream.withWriter { it << form }
        login.responseCode
        extractCookies(login, cookies)

        def landing = new URL(login.getHeaderField("Location")).openConnection()
        landing.setRequestProperty("Cookie", cookieHeader(cookies))
        landing.responseCode
        extractCookies(landing, cookies)
        cookies
    }

    void "quick-add, set daily recurrence, complete twice: one regeneration, then a stopped series generates nothing"() {
        given:
        Map<String, String> cookies = loggedInCookies()
        LocalDate today = LocalDate.now()

        and: "a quick-added task"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter { it << "title=${URLEncoder.encode('Recurrence-Flow-Task', 'UTF-8')}" }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)
        Long firstId
        Task.withTransaction { firstId = Task.findByTitle("Recurrence-Flow-Task").id }

        when: "setting a daily recurrence on it"
        def setRec = new URL("http://localhost:${serverPort}/task/setRecurrence/${firstId}").openConnection()
        setRec.requestMethod = "POST"
        setRec.doOutput = true
        setRec.instanceFollowRedirects = false
        setRec.setRequestProperty("Cookie", cookieHeader(cookies))
        setRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        setRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRec.outputStream.withWriter { it << "type=DAILY&interval=1" }
        setRec.responseCode
        extractCookies(setRec, cookies)

        and: "completing it"
        def complete1 = new URL("http://localhost:${serverPort}/task/complete/${firstId}").openConnection()
        complete1.requestMethod = "POST"
        complete1.doOutput = true
        complete1.instanceFollowRedirects = false
        complete1.setRequestProperty("Cookie", cookieHeader(cookies))
        complete1.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        complete1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        complete1.outputStream.withWriter { it << "" }
        complete1.responseCode
        extractCookies(complete1, cookies)

        // Extraction happens here, in a when/and block, not in then: -- a bare
        // Task.withTransaction {...} call as a top-level then: statement would itself
        // be auto-wrapped by Spock as a boolean condition, silently discarding every
        // statement in the closure except its last.
        and: "reading back the newly spawned occurrence"
        Long secondId
        LocalDate secondDueDate
        boolean secondActive
        int openCount
        Task.withTransaction {
            def open = Task.findAllByTitleAndStatus("Recurrence-Flow-Task", TaskStatus.OPEN)
            openCount = open.size()
            secondId = open[0].id
            secondDueDate = open[0].dueDate
            secondActive = open[0].recurrenceRule?.active
        }

        then: "exactly one new occurrence exists, due tomorrow, still recurring"
        openCount == 1
        secondDueDate == today.plusDays(1)
        secondActive

        when: "stopping the series on the new occurrence"
        def stopRec = new URL("http://localhost:${serverPort}/task/stopRecurrence/${secondId}").openConnection()
        stopRec.requestMethod = "POST"
        stopRec.doOutput = true
        stopRec.instanceFollowRedirects = false
        stopRec.setRequestProperty("Cookie", cookieHeader(cookies))
        stopRec.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        stopRec.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        stopRec.outputStream.withWriter { it << "" }
        stopRec.responseCode
        extractCookies(stopRec, cookies)

        and: "completing the (now non-recurring) second occurrence"
        def complete2 = new URL("http://localhost:${serverPort}/task/complete/${secondId}").openConnection()
        complete2.requestMethod = "POST"
        complete2.doOutput = true
        complete2.instanceFollowRedirects = false
        complete2.setRequestProperty("Cookie", cookieHeader(cookies))
        complete2.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        complete2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        complete2.outputStream.withWriter { it << "" }
        complete2.responseCode

        then: "no third occurrence was created -- still only the two prior tasks, both now DONE"
        Task.withTransaction {
            def all = Task.findAllByTitle("Recurrence-Flow-Task")
            all.size() == 2 && all.every { it.status == TaskStatus.DONE }
        }

        cleanup:
        Task.withTransaction { Task.findAllByTitle("Recurrence-Flow-Task")*.delete(flush: true) }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.RecurrenceFlowIntegrationSpec"`
Expected: PASS.

- [ ] **Step 3: Run the full suite one more time**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL — every unit and integration spec passes, across Phase 1, Phase 2, date-parsing, and the new recurrence feature.

- [ ] **Step 4: Commit**

```bash
git add src/integration-test/groovy/taskboard/RecurrenceFlowIntegrationSpec.groovy
git commit -m "test: add end-to-end recurring-tasks HTTP flow test"
```
