# Family Taskboard — Phase 2 (Projects) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tasks optionally belong to a Project (name + color), with inline
project management, a project filter on the task list, and project selection
from quick-add and each task card.

**Architecture:** A new `Project` GORM domain class (name, color) with a plain
(non-`belongsTo`/`hasMany`) `Task.project` reference. `ProjectService` owns
create/update/delete, with delete explicitly nullifying `task.project` on
referencing tasks before removing the project row — no GORM cascade-delete
involved. `ProjectController` renders an HTMX-swappable management fragment;
`TaskController` gains a `list()` filter action and an `assignProject()`
action following the exact pattern already established by Phase 1's
`assign()` (assignee) action.

**Tech Stack:** Grails 7.1.1, GORM/Hibernate, HTMX 2.0.3, GSP, Spock (unit +
`@Integration`) — unchanged from Phase 1, no new dependencies.

## Global Constraints

- Grails 7.1.1 / Java 21 / GORM / HTMX 2.0.3 / Spock — same stack as Phase 1, no version changes.
- `Project` and `Task` are connected via a plain object reference only — **no** GORM `belongsTo`/`hasMany` pair between them (would cascade-delete tasks on project deletion, which the spec explicitly forbids). See spec §2.
- `Project.color` must match `^#[0-9A-Fa-f]{6}$` (spec §5).
- `dbCreate: update` is already configured (`grails-app/conf/application.yml`) — GORM auto-adds the new table/column; no manual migration task needed.
- No new Spring Security wiring: `/project/**` is not in `SecurityConfig`'s permitAll matcher list, so it's already covered by the existing `anyRequest().authenticated()` rule. Do not touch `SecurityConfig.groovy`.
- No new `UrlMappings.groovy` entries needed: the existing generic `"/$controller/$action?/$id?(.$format)?"` mapping already resolves every new controller/action combination introduced here, exactly as it already does for `/task/complete/<id>` and `/task/assign/<id>`.
- Follow the established defensive-null pattern from Phase 1's review fixes: service methods that take an id return `null` (not throw) for an id that no longer exists.
- Unit tests (`src/test/groovy`) for pure domain/service logic; `@Integration` tests (`src/integration-test/groovy`) for anything touching Spring/HTTP/DB — matches the existing split in this codebase.

---

### Task 1: `Project` domain class + `Task.project` field

**Files:**
- Create: `grails-app/domain/taskboard/Project.groovy`
- Modify: `grails-app/domain/taskboard/Task.groovy`
- Test: `src/test/groovy/taskboard/ProjectSpec.groovy`

**Interfaces:**
- Consumes: nothing (foundational domain class).
- Produces: `Project { String name, String color }` with constraints `name blank:false`, `color matches: /^#[0-9A-Fa-f]{6}$/`. `Task.project` — a new nullable `Project` field on the existing `Task` domain class.

- [ ] **Step 1: Write the failing unit test**

Create `src/test/groovy/taskboard/ProjectSpec.groovy`:

```groovy
package taskboard

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class ProjectSpec extends Specification implements DomainUnitTest<Project> {

    void "a project with a name and a valid hex color is valid"() {
        expect:
        new Project(name: "Garten", color: "#3B82F6").validate()
    }

    void "blank name is invalid"() {
        expect:
        !new Project(name: "", color: "#3B82F6").validate(['name'])
    }

    void "color must be a 6-digit hex code"() {
        expect:
        !new Project(name: "Garten", color: "not-a-color").validate(['color'])
        !new Project(name: "Garten", color: "#ABC").validate(['color'])
        new Project(name: "Garten", color: "#abc123").validate(['color'])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "taskboard.ProjectSpec"`
Expected: compilation FAILURE — `Project` does not exist yet (`unable to resolve class Project`).

- [ ] **Step 3: Implement `Project.groovy`**

Create `grails-app/domain/taskboard/Project.groovy`:

```groovy
package taskboard

/**
 * A label for grouping tasks. Deliberately has no GORM hasMany/belongsTo
 * relationship with Task -- see ProjectService.delete() for why: a
 * bidirectional belongsTo/hasMany pair would make GORM cascade-delete every
 * task referencing a project when that project is deleted, which is wrong
 * here (a project is just a grouping label, not an owner of its tasks).
 */
class Project {
    String name
    String color

    static constraints = {
        name blank: false, nullable: false
        color blank: false, nullable: false, matches: /^#[0-9A-Fa-f]{6}$/
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "taskboard.ProjectSpec"`
Expected: PASS (3 tests).

- [ ] **Step 5: Add the nullable `project` field to `Task`**

Replace the full contents of `grails-app/domain/taskboard/Task.groovy` with:

```groovy
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
```

- [ ] **Step 6: Run the full unit test suite to confirm nothing broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all unit specs pass, including the pre-existing `TaskSpec`, `UserSpec`, `UrgencyServiceSpec`, `PushServicePayloadSpec` (adding a nullable field must not affect any of them).

- [ ] **Step 7: Commit**

```bash
git add grails-app/domain/taskboard/Project.groovy grails-app/domain/taskboard/Task.groovy src/test/groovy/taskboard/ProjectSpec.groovy
git commit -m "feat: add Project domain and optional Task.project field"
```

---

### Task 2: `ProjectService` (create/update/delete with nullify-on-delete)

**Files:**
- Create: `grails-app/services/taskboard/ProjectService.groovy`
- Test: `src/integration-test/groovy/taskboard/ProjectServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `Project` domain class, `Task.project` field (both from Task 1).
- Produces:
  - `ProjectService.create(String name, String color) -> Project` (returns `null` if invalid, matching GORM's plain `save()` semantics — does not throw).
  - `ProjectService.update(Long id, String name, String color) -> Project` (returns `null` for an unknown id or invalid input).
  - `ProjectService.delete(Long id)` (`void`; no-op for an unknown id; nullifies `task.project` on every referencing task before deleting the project row).

- [ ] **Step 1: Write the failing integration tests**

Create `src/integration-test/groovy/taskboard/ProjectServiceIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import java.time.LocalDate

@Integration
@Rollback
class ProjectServiceIntegrationSpec extends Specification {

    ProjectService projectService

    void "create saves a project with name and color"() {
        when:
        def p = projectService.create("Garten", "#3B82F6")

        then:
        p != null
        p.id != null
        p.name == "Garten"
        p.color == "#3B82F6"
    }

    void "create returns null for an invalid color"() {
        expect:
        projectService.create("Garten", "not-a-color") == null
    }

    void "update changes name and color"() {
        given:
        def p = projectService.create("Garten", "#3B82F6")

        when:
        def updated = projectService.update(p.id, "Haus", "#10B981")

        then:
        updated.name == "Haus"
        updated.color == "#10B981"
        Project.get(p.id).name == "Haus"
    }

    void "update returns null for an unknown id"() {
        expect:
        projectService.update(-1L, "X", "#000000") == null
    }

    void "delete nullifies project on referencing tasks before removing it"() {
        given:
        def u = new User(username: "proj-svc-u", password: "p",
            displayName: "U", apiToken: "psu").save(flush: true)
        def p = projectService.create("Garten", "#3B82F6")
        def t = new Task(title: "Rasen mähen", dueDate: LocalDate.now(),
            priority: Priority.LOW, createdBy: u, project: p).save(flush: true)

        when:
        projectService.delete(p.id)

        then:
        Project.get(p.id) == null
        Task.get(t.id).project == null
    }

    void "delete on an unknown id is a no-op"() {
        when:
        projectService.delete(-1L)

        then:
        noExceptionThrown()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.ProjectServiceIntegrationSpec"`
Expected: compilation FAILURE — `ProjectService` does not exist yet.

- [ ] **Step 3: Implement `ProjectService.groovy`**

Create `grails-app/services/taskboard/ProjectService.groovy`:

```groovy
package taskboard

import grails.gorm.transactions.Transactional

/** CRUD for Project labels used to group tasks (see Project's docblock for why
 *  there's no GORM belongsTo/hasMany relationship to Task). */
@Transactional
class ProjectService {

    Project create(String name, String color) {
        def p = new Project(name: name, color: color)
        p.save() ? p : null
    }

    Project update(Long id, String name, String color) {
        def p = Project.get(id)
        if (!p) return null
        p.name = name
        p.color = color
        p.save() ? p : null
    }

    /** Nullifies `project` on every referencing task BEFORE deleting the project
     *  itself, so tasks are never destroyed and no dangling foreign key is
     *  possible -- deliberately not relying on GORM's belongsTo/hasMany cascade
     *  for this (see Project's docblock). A no-op for an unknown id. */
    void delete(Long id) {
        def p = Project.get(id)
        if (!p) return
        Task.findAllByProject(p).each { Task t ->
            t.project = null
            t.save(failOnError: true)
        }
        p.delete(flush: true)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.ProjectServiceIntegrationSpec"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add grails-app/services/taskboard/ProjectService.groovy src/integration-test/groovy/taskboard/ProjectServiceIntegrationSpec.groovy
git commit -m "feat: add ProjectService with nullify-on-delete"
```

---

### Task 3: `TaskService` project-filter queries and `assignProject`

**Files:**
- Modify: `grails-app/services/taskboard/TaskService.groovy`
- Modify: `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `Project` domain class (Task 1).
- Produces:
  - `TaskService.createTask(Map params, User creator)` — `params` map now also accepts an optional `project: Project` entry (`null` if absent, same as the existing `assignedTo` entry already works).
  - `TaskService.tasksForProject(Project project) -> List<Task>` (non-DONE tasks in that project, sorted by `dueDate` ascending).
  - `TaskService.tasksWithoutProject() -> List<Task>` (non-DONE tasks with no project, same sort).
  - `TaskService.assignProject(Long taskId, Project project) -> Task` (`null` for an unknown task id, same defensive pattern as `complete()`/`assignTask()`).

- [ ] **Step 1: Write the failing integration tests**

Append these test methods to `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`, just before the final closing `}` of the class:

```groovy
    void "createTask stores the given project"() {
        given:
        def u = new User(username: "proj-u1", password: "p",
            displayName: "U", apiToken: "pu1").save(flush: true)
        def project = new Project(name: "Garten", color: "#3B82F6").save(flush: true)

        when:
        def t = taskService.createTask([title: "Rasen mähen",
            dueDate: LocalDate.now(), priority: Priority.LOW, project: project], u)

        then:
        t.project?.id == project.id
    }

    void "tasksForProject returns only tasks in that project, sorted by dueDate"() {
        given:
        def u = new User(username: "proj-u2", password: "p",
            displayName: "U", apiToken: "pu2").save(flush: true)
        def projectA = new Project(name: "A", color: "#3B82F6").save(flush: true)
        def projectB = new Project(name: "B", color: "#10B981").save(flush: true)
        taskService.createTask([title: "in A, later",
            dueDate: LocalDate.now().plusDays(5), priority: Priority.LOW, project: projectA], u)
        taskService.createTask([title: "in A, soon",
            dueDate: LocalDate.now().plusDays(1), priority: Priority.LOW, project: projectA], u)
        taskService.createTask([title: "in B",
            dueDate: LocalDate.now(), priority: Priority.LOW, project: projectB], u)

        when:
        def list = taskService.tasksForProject(projectA)

        then:
        list*.title == ["in A, soon", "in A, later"]
    }

    void "tasksWithoutProject returns only tasks without a project"() {
        given:
        def u = new User(username: "proj-u3", password: "p",
            displayName: "U", apiToken: "pu3").save(flush: true)
        def project = new Project(name: "C", color: "#3B82F6").save(flush: true)
        taskService.createTask([title: "no project",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)
        taskService.createTask([title: "has project",
            dueDate: LocalDate.now(), priority: Priority.LOW, project: project], u)

        when:
        def list = taskService.tasksWithoutProject()

        then:
        list*.title == ["no project"]
    }

    void "assignProject sets the task's project"() {
        given:
        def u = new User(username: "proj-u4", password: "p",
            displayName: "U", apiToken: "pu4").save(flush: true)
        def project = new Project(name: "D", color: "#3B82F6").save(flush: true)
        def t = taskService.createTask([title: "x",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)

        when:
        def result = taskService.assignProject(t.id, project)

        then:
        result.project.id == project.id
        Task.get(t.id).project.id == project.id
    }

    void "assignProject returns null instead of throwing for an id that no longer exists"() {
        given:
        def project = new Project(name: "E", color: "#3B82F6").save(flush: true)

        expect:
        taskService.assignProject(-1L, project) == null
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: compilation FAILURE — `TaskService` has no `tasksForProject`/`tasksWithoutProject`/`assignProject` methods yet, and `createTask` does not persist `project`.

- [ ] **Step 3: Implement the `TaskService` changes**

Replace the full contents of `grails-app/services/taskboard/TaskService.groovy` with:

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate

/** Shared by both task-creation paths: the web UI's quick-add and the REST quick-add endpoint. */
@Transactional
class TaskService {

    PushService pushService

    /** Used identically by TaskController.quickAdd() and ApiTaskController.quick(). */
    Task createTask(Map params, User creator) {
        def task = new Task(
            title: params.title,
            dueDate: params.dueDate ?: LocalDate.now(),
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: PASS (9 tests: 4 pre-existing + 5 new).

- [ ] **Step 5: Commit**

```bash
git add grails-app/services/taskboard/TaskService.groovy src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy
git commit -m "feat: add project-filter queries and assignProject to TaskService"
```

---

### Task 4: `ProjectController` + inline project management UI

**Files:**
- Create: `grails-app/controllers/taskboard/ProjectController.groovy`
- Create: `grails-app/views/project/_manage.gsp`
- Modify: `grails-app/controllers/taskboard/TaskController.groovy:26-29` (the `index()` action)
- Modify: `grails-app/views/task/index.gsp`
- Test: `src/integration-test/groovy/taskboard/ProjectControllerIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `ProjectService.create/update/delete` (Task 2), `Project.list()` (Task 1).
- Produces: `ProjectController.list()`, `.create()`, `.update(Long id)`, `.delete(Long id)` — each renders the `project/manage` template fragment with model `[projects: List<Project>, error: String?]`. That template is reachable both standalone (from `ProjectController`'s own actions, relative template resolution) and embedded from `task/index.gsp` via the absolute path `/project/manage`.

- [ ] **Step 1: Write the failing integration tests**

Create `src/integration-test/groovy/taskboard/ProjectControllerIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * Exercises ProjectController over real HTTP, same login-flow pattern as
 * TaskControllerSessionFlowIntegrationSpec. No @Rollback: these HTTP calls run
 * on a separate server thread, so a test-level transaction wouldn't see them
 * anyway (same reasoning as ApiTaskControllerIntegrationSpec) -- rows are
 * cleaned up explicitly instead.
 */
@Integration
class ProjectControllerIntegrationSpec extends Specification {

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

    void "unauthenticated request to /project/list redirects to login"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/project/list").openConnection()
        conn.instanceFollowRedirects = false

        expect:
        conn.responseCode == 302
        conn.getHeaderField("Location")?.contains("/login/auth")
    }

    void "create then update then delete a project via HTTP round-trips correctly"() {
        given:
        Map<String, String> cookies = loggedInCookies()

        when: "creating a project"
        def create = new URL("http://localhost:${serverPort}/project/create").openConnection()
        create.requestMethod = "POST"
        create.doOutput = true
        create.instanceFollowRedirects = false
        create.setRequestProperty("Cookie", cookieHeader(cookies))
        create.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        create.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        create.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Controller-Flow-Project', 'UTF-8')}&color=${URLEncoder.encode('#3B82F6', 'UTF-8')}"
        }
        int createStatus = create.responseCode
        String createBody = create.inputStream.text

        then:
        createStatus == 200
        createBody.contains("Controller-Flow-Project")

        when: "updating it"
        Long projectId
        Project.withTransaction { projectId = Project.findByName("Controller-Flow-Project").id }
        def update = new URL("http://localhost:${serverPort}/project/update/${projectId}").openConnection()
        update.requestMethod = "POST"
        update.doOutput = true
        update.instanceFollowRedirects = false
        update.setRequestProperty("Cookie", cookieHeader(cookies))
        update.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        update.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        update.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Renamed-Project', 'UTF-8')}&color=${URLEncoder.encode('#10B981', 'UTF-8')}"
        }
        int updateStatus = update.responseCode
        String updateBody = update.inputStream.text

        then:
        updateStatus == 200
        updateBody.contains("Renamed-Project")
        !updateBody.contains("Controller-Flow-Project")

        when: "submitting an invalid color"
        def invalid = new URL("http://localhost:${serverPort}/project/update/${projectId}").openConnection()
        invalid.requestMethod = "POST"
        invalid.doOutput = true
        invalid.instanceFollowRedirects = false
        invalid.setRequestProperty("Cookie", cookieHeader(cookies))
        invalid.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        invalid.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        invalid.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Renamed-Project', 'UTF-8')}&color=not-a-color"
        }
        String invalidBody = invalid.inputStream.text

        then: "the invalid submission is rejected and the prior valid values are unchanged"
        invalidBody.contains("Ungültiger Name oder Farbe")
        Project.withTransaction { Project.get(projectId).color == "#10B981" }

        when: "deleting it"
        def delete = new URL("http://localhost:${serverPort}/project/delete/${projectId}").openConnection()
        delete.requestMethod = "POST"
        delete.doOutput = true
        delete.instanceFollowRedirects = false
        delete.setRequestProperty("Cookie", cookieHeader(cookies))
        delete.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        delete.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        delete.outputStream.withWriter { it << "" }
        int deleteStatus = delete.responseCode
        String deleteBody = delete.inputStream.text

        then:
        deleteStatus == 200
        !deleteBody.contains("Renamed-Project")
        Project.withTransaction { Project.get(projectId) == null }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.ProjectControllerIntegrationSpec"`
Expected: compilation/startup FAILURE — `ProjectController` does not exist yet.

- [ ] **Step 3: Implement `ProjectController.groovy`**

Create `grails-app/controllers/taskboard/ProjectController.groovy`:

```groovy
package taskboard

/**
 * Inline project management (create/edit/delete), used from the collapsible
 * "Projekte verwalten" section on the task list page (see task/index.gsp).
 * No @Secured annotation -- relies on SecurityConfig's anyRequest().authenticated(),
 * same as TaskController; /project/** is not in the permitAll matcher list.
 */
class ProjectController {

    ProjectService projectService

    def list() {
        render template: 'manage', model: [projects: Project.list(), error: null]
    }

    def create() {
        def result = projectService.create(params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def update(Long id) {
        def result = projectService.update(id, params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def delete(Long id) {
        projectService.delete(id)
        render template: 'manage', model: [projects: Project.list(), error: null]
    }
}
```

- [ ] **Step 4: Create the management fragment template**

Create `grails-app/views/project/_manage.gsp`:

```gsp
<div id="project-manage">
    <g:if test="${error}">
        <p class="project-error">${error}</p>
    </g:if>
    <ul class="project-list">
        <g:each in="${projects}" var="p">
            <li>
                <form hx-post="${createLink(controller: 'project', action: 'update', id: p.id)}"
                      hx-target="#project-manage" hx-swap="outerHTML">
                    <input type="text" name="name" value="${p.name}" required>
                    <input type="color" name="color" value="${p.color}">
                    <button type="submit">Speichern</button>
                </form>
                <button type="button"
                        hx-post="${createLink(controller: 'project', action: 'delete', id: p.id)}"
                        hx-target="#project-manage" hx-swap="outerHTML">Löschen</button>
            </li>
        </g:each>
    </ul>
    <form hx-post="${createLink(controller: 'project', action: 'create')}"
          hx-target="#project-manage" hx-swap="outerHTML">
        <input type="text" name="name" placeholder="Neues Projekt…" required>
        <input type="color" name="color" value="#3B82F6">
        <button type="submit">Anlegen</button>
    </form>
</div>
```

Note: the existing `document.body.addEventListener('htmx:configRequest', ...)` shim in `task/index.gsp` already attaches `X-XSRF-TOKEN` to every HTMX request page-wide (it listens on `document.body`, catching the bubbled event from any element), so these new forms need no extra CSRF wiring.

- [ ] **Step 5: Add `projects` to `TaskController.index()`'s model**

In `grails-app/controllers/taskboard/TaskController.groovy`, replace:

```groovy
    def index() {
        [tasks: taskService.openTasksSorted(), urgencyService: urgencyService,
         today: LocalDate.now(), users: User.list()]
    }
```

with:

```groovy
    def index() {
        [tasks: taskService.openTasksSorted(), urgencyService: urgencyService,
         today: LocalDate.now(), users: User.list(), projects: Project.list()]
    }
```

- [ ] **Step 6: Wire the collapsible management section into `index.gsp`**

In `grails-app/views/task/index.gsp`, replace:

```gsp
    <header class="navbar"><h1>Meine Tasks</h1></header>
    <main id="task-list">
```

with:

```gsp
    <header class="navbar"><h1>Meine Tasks</h1></header>

    <details id="project-manage-section">
        <summary>Projekte verwalten</summary>
        <g:render template="/project/manage" model="[projects: projects, error: null]"/>
    </details>

    <main id="task-list">
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.ProjectControllerIntegrationSpec"`
Expected: PASS (2 tests).

- [ ] **Step 8: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL, all unit and integration tests green (existing `TaskControllerSessionFlowIntegrationSpec` etc. must still pass with the `index()` model change).

- [ ] **Step 9: Commit**

```bash
git add grails-app/controllers/taskboard/ProjectController.groovy grails-app/views/project/_manage.gsp grails-app/controllers/taskboard/TaskController.groovy grails-app/views/task/index.gsp src/integration-test/groovy/taskboard/ProjectControllerIntegrationSpec.groovy
git commit -m "feat: inline project management UI"
```

---

### Task 5: Filter pills, quick-add project select, task-card project chip/select

**Files:**
- Modify: `grails-app/controllers/taskboard/TaskController.groovy`
- Modify: `grails-app/views/task/_list.gsp`
- Modify: `grails-app/views/task/_card.gsp`
- Modify: `grails-app/views/task/index.gsp`
- Modify: `grails-app/assets/stylesheets/taskboard.css`
- Test: `src/integration-test/groovy/taskboard/ProjectFlowIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `TaskService.tasksForProject/tasksWithoutProject/assignProject` (Task 3), `ProjectController` + `/project/*` routes (Task 4).
- Produces: `TaskController.list()` (GET; `params.project` is a Project id, the literal `"none"`, or absent — falls back to the unfiltered list for an id that no longer resolves), `TaskController.assignProject(Long id)` (POST).

- [ ] **Step 1: Write the failing end-to-end integration test**

Create `src/integration-test/groovy/taskboard/ProjectFlowIntegrationSpec.groovy`:

```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext

/**
 * End-to-end HTTP flow tying Project and Task together: create a project,
 * quick-add a task into it, filter the list by that project's pill, then
 * delete the project and confirm the task survives with no project. Matches
 * the scenario required by the Phase 2 design spec §6.
 */
@Integration
class ProjectFlowIntegrationSpec extends Specification {

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

    void "create project, quick-add a task into it, filter by it, then delete the project"() {
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

        when: "creating a project"
        def create = new URL("http://localhost:${serverPort}/project/create").openConnection()
        create.requestMethod = "POST"
        create.doOutput = true
        create.instanceFollowRedirects = false
        create.setRequestProperty("Cookie", cookieHeader(cookies))
        create.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        create.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        create.outputStream.withWriter {
            it << "name=${URLEncoder.encode('Flow-Project', 'UTF-8')}&color=${URLEncoder.encode('#3B82F6', 'UTF-8')}"
        }
        create.responseCode
        extractCookies(create, cookies)

        Long projectId
        Project.withTransaction { projectId = Project.findByName("Flow-Project").id }

        and: "quick-adding a task into that project"
        def quickAdd = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd.requestMethod = "POST"
        quickAdd.doOutput = true
        quickAdd.instanceFollowRedirects = false
        quickAdd.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd.outputStream.withWriter {
            it << "title=${URLEncoder.encode('Flow-Task-In-Project', 'UTF-8')}&project=${projectId}"
        }
        quickAdd.responseCode
        extractCookies(quickAdd, cookies)

        and: "quick-adding a second task NOT in that project, so filtering can be verified"
        def quickAdd2 = new URL("http://localhost:${serverPort}/task/quickAdd").openConnection()
        quickAdd2.requestMethod = "POST"
        quickAdd2.doOutput = true
        quickAdd2.instanceFollowRedirects = false
        quickAdd2.setRequestProperty("Cookie", cookieHeader(cookies))
        quickAdd2.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        quickAdd2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        quickAdd2.outputStream.withWriter {
            it << "title=${URLEncoder.encode('Flow-Task-Outside-Project', 'UTF-8')}"
        }
        quickAdd2.responseCode
        extractCookies(quickAdd2, cookies)

        then: "the task was created with the project set"
        Task.withTransaction { Task.findByTitle("Flow-Task-In-Project").project?.id == projectId }

        when: "filtering the list by that project's pill"
        def filtered = new URL("http://localhost:${serverPort}/task/list?project=${projectId}").openConnection()
        filtered.setRequestProperty("Cookie", cookieHeader(cookies))
        String filteredBody = filtered.inputStream.text

        then: "only the task in that project appears"
        filteredBody.contains("Flow-Task-In-Project")
        !filteredBody.contains("Flow-Task-Outside-Project")

        when: "deleting the project"
        def delete = new URL("http://localhost:${serverPort}/project/delete/${projectId}").openConnection()
        delete.requestMethod = "POST"
        delete.doOutput = true
        delete.instanceFollowRedirects = false
        delete.setRequestProperty("Cookie", cookieHeader(cookies))
        delete.setRequestProperty("X-XSRF-TOKEN", cookies['XSRF-TOKEN'])
        delete.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        delete.outputStream.withWriter { it << "" }
        delete.responseCode

        then: "the task still exists, now with no project"
        Task.withTransaction { Task.findByTitle("Flow-Task-In-Project").project == null }

        when: "filtering by the now-deleted project id falls back to the unfiltered list"
        def staleFilter = new URL("http://localhost:${serverPort}/task/list?project=${projectId}").openConnection()
        staleFilter.setRequestProperty("Cookie", cookieHeader(cookies))
        int staleFilterStatus = staleFilter.responseCode
        String staleFilterBody = staleFilter.inputStream.text

        then:
        staleFilterStatus == 200
        staleFilterBody.contains("Flow-Task-In-Project")
        staleFilterBody.contains("Flow-Task-Outside-Project")

        cleanup:
        Task.withTransaction {
            Task.findAllByTitleInList(["Flow-Task-In-Project", "Flow-Task-Outside-Project"])*.delete(flush: true)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.ProjectFlowIntegrationSpec"`
Expected: FAILURE — `/task/list?project=...` doesn't exist yet (404), and quick-add doesn't accept a `project` param yet.

- [ ] **Step 3: Update `TaskController.groovy`**

Replace the full contents of `grails-app/controllers/taskboard/TaskController.groovy` with:

```groovy
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
            dueDate: params.dueDate ? LocalDate.parse(params.dueDate) : LocalDate.now(),
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
        } else {
            Project project = Project.get(selectedProject as Long)
            if (project) {
                tasks = taskService.tasksForProject(project)
            } else {
                tasks = taskService.openTasksSorted()
                selectedProject = null
            }
        }
        render template: 'list', model: [tasks: tasks, urgencyService: urgencyService,
            today: LocalDate.now(), users: User.list(), projects: Project.list(),
            selectedProject: selectedProject]
    }
}
```

- [ ] **Step 4: Update `_list.gsp`**

Replace the full contents of `grails-app/views/task/_list.gsp` with:

```gsp
<div id="project-filter">
    <a href="#" class="project-pill ${selectedProject == null ? 'active' : ''}"
       hx-get="${createLink(action: 'list')}" hx-target="#task-list" hx-swap="innerHTML">Alle</a>
    <a href="#" class="project-pill ${selectedProject == 'none' ? 'active' : ''}"
       hx-get="${createLink(action: 'list', params: [project: 'none'])}"
       hx-target="#task-list" hx-swap="innerHTML">Kein Projekt</a>
    <g:each in="${projects}" var="p">
        <a href="#" class="project-pill ${selectedProject == p.id.toString() ? 'active' : ''}"
           style="background-color: ${p.color};"
           hx-get="${createLink(action: 'list', params: [project: p.id])}"
           hx-target="#task-list" hx-swap="innerHTML">${p.name}</a>
    </g:each>
</div>
<g:each in="${tasks}" var="task">
    <g:render template="card"
        model="[task: task, color: urgencyService.colorFor(task, today), users: users, projects: projects]"/>
</g:each>
```

- [ ] **Step 5: Update `_card.gsp`**

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
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
</div>
```

- [ ] **Step 6: Update `index.gsp`**

In `grails-app/views/task/index.gsp`, replace:

```gsp
    <main id="task-list">
        <g:render template="list"
            model="[tasks: tasks, urgencyService: urgencyService, today: today, users: users]"/>
    </main>

    <form id="quick-add" hx-post="${createLink(action: 'quickAdd')}"
          hx-target="#task-list" hx-swap="innerHTML">
        <input type="text" name="title" id="title-input" placeholder="Neuer Task…" required>
        <button type="button" id="mic-btn" class="fab" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit">+</button>
    </form>
```

with:

```gsp
    <main id="task-list">
        <g:render template="list"
            model="[tasks: tasks, urgencyService: urgencyService, today: today, users: users, projects: projects, selectedProject: selectedProject]"/>
    </main>

    <form id="quick-add" hx-post="${createLink(action: 'quickAdd')}"
          hx-target="#task-list" hx-swap="innerHTML">
        <input type="text" name="title" id="title-input" placeholder="Neuer Task…" required>
        <select name="project">
            <option value="">Kein Projekt</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}">${p.name}</option>
            </g:each>
        </select>
        <button type="button" id="mic-btn" class="fab" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit">+</button>
    </form>
```

- [ ] **Step 7: Update `taskboard.css`**

Replace the full contents of `grails-app/assets/stylesheets/taskboard.css` with:

```css
.task-card { border-left: 4px solid; padding: 10px 12px; margin: 8px;
    border-radius: 12px; background: #fff; }
.task-card.green   { border-left-color: #3B6D11; }
.task-card.yellow  { border-left-color: #BA7517; }
.task-card.orange  { border-left-color: #D85A30; }
.task-card.red     { border-left-color: #E24B4A; }
.task-card.darkred { border-left-color: #791F1F; }
.fab { position: fixed; bottom: 16px; right: 16px; width: 56px; height: 56px;
    border-radius: 50%; font-size: 22px; }
.project-chip { display: inline-block; padding: 2px 8px; border-radius: 10px;
    font-size: 0.75em; color: #fff; margin-bottom: 4px; }
.project-pill { display: inline-block; padding: 4px 10px; margin: 2px;
    border-radius: 12px; background: #ddd; text-decoration: none; color: #222;
    font-size: 0.85em; }
.project-pill.active { outline: 2px solid #222; font-weight: bold; }
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.ProjectFlowIntegrationSpec"`
Expected: PASS.

- [ ] **Step 9: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL — every unit and integration spec passes, including all pre-existing Phase 1 specs (`TaskControllerSessionFlowIntegrationSpec`, `SecurityFilterChainIntegrationSpec`, etc. — the `index()`/`quickAdd()`/`complete()`/`assign()` model changes must not break them).

- [ ] **Step 10: Manually verify in a browser (or via curl, per this project's established practice for anything touching the security filter chain or GSP rendering)**

Run: `./gradlew bootRun -Dgrails.env=test`, then in another terminal or a browser:
1. Log in as `lars`/`changeme`.
2. Expand "Projekte verwalten", create a project with a name and color.
3. Confirm a filter pill for it appears above the task list, colored correctly.
4. Quick-add a task, selecting that project — confirm the card shows the project chip.
5. Click the project's filter pill — confirm only that task shows.
6. Click "Alle" — confirm all tasks show again.
7. Delete the project from the management section — confirm the task's chip disappears and it still appears under "Alle".

- [ ] **Step 11: Commit**

```bash
git add grails-app/controllers/taskboard/TaskController.groovy grails-app/views/task/_list.gsp grails-app/views/task/_card.gsp grails-app/views/task/index.gsp grails-app/assets/stylesheets/taskboard.css src/integration-test/groovy/taskboard/ProjectFlowIntegrationSpec.groovy
git commit -m "feat: project filter pills, quick-add project select, task-card project chip/select"
```
