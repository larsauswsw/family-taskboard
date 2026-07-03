# Natural-Language Date Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recognize a limited set of German date phrases ("heute", "morgen", "übermorgen", weekday names, "in N Tagen"/"in N Wochen") in quick-add text, strip the matched phrase from the title, and use it as the task's due date — for both the web quick-add form and the REST quick-add endpoint.

**Architecture:** A new stateless `DateParsingService` (deterministic — takes `today` as a parameter rather than calling `LocalDate.now()` internally, same pattern `UrgencyService.colorFor(task, today)` already uses) does the actual regex-based extraction, returning a small `ParsedTitle` value object. `TaskService.createTask()` — already the shared choke point for both quick-add paths — calls it only when the caller didn't pass an explicit `dueDate`, so an explicit date always wins over anything found in the text.

**Tech Stack:** Grails 7.1.1, GORM, Spock (unit + `@Integration`), `java.util.regex` — no new dependencies.

## Global Constraints

- Grails 7.1.1 / Java 21 / Spock — same stack as Phase 1/2, no version changes.
- Recognized phrases (case-insensitive, whole-word only): `heute`, `morgen`, `übermorgen`, weekday names (`Montag`...`Sonntag`, plus the `-s` adverbial form e.g. `montags`), `in N Tagen`/`in einem Tag`, `in N Wochen`/`in einer Woche`. Nothing else.
- A weekday name always resolves to the next occurrence **strictly after** `today` — if today already is that weekday, the result is 7 days later, never today.
- If more than one phrase appears in the text, the **leftmost match wins**.
- The matched phrase (plus an immediately preceding `bis`/`am`/`in`, if directly adjacent) is removed from the title — unless that would leave the title blank (`Task.title` has `blank: false`), in which case the full original text is kept as the title while the parsed date is still used.
- `TaskService.createTask()` only parses when `params.dueDate` is absent/null. An explicit `dueDate` always wins and skips parsing entirely (title stays exactly as given).
- No new dependencies; this is a hand-rolled regex parser, not an external NLP library (see design spec §2 for why).

---

### Task 1: `ParsedTitle` value class + `DateParsingService`

**Files:**
- Create: `src/main/groovy/taskboard/ParsedTitle.groovy`
- Create: `grails-app/services/taskboard/DateParsingService.groovy`
- Test: `src/test/groovy/taskboard/DateParsingServiceSpec.groovy`

**Interfaces:**
- Consumes: nothing (foundational, pure logic, no DB).
- Produces: `ParsedTitle { LocalDate date, String title }` (plain, non-persistent value class). `DateParsingService.parse(String title, LocalDate today) -> ParsedTitle`.

- [ ] **Step 1: Write the failing unit tests**

Create `src/test/groovy/taskboard/DateParsingServiceSpec.groovy`:

```groovy
package taskboard

import spock.lang.Specification
import java.time.DayOfWeek
import java.time.LocalDate

class DateParsingServiceSpec extends Specification {

    private static final Map<DayOfWeek, String> GERMAN_NAME = [
        (DayOfWeek.MONDAY): 'Montag', (DayOfWeek.TUESDAY): 'Dienstag',
        (DayOfWeek.WEDNESDAY): 'Mittwoch', (DayOfWeek.THURSDAY): 'Donnerstag',
        (DayOfWeek.FRIDAY): 'Freitag', (DayOfWeek.SATURDAY): 'Samstag',
        (DayOfWeek.SUNDAY): 'Sonntag'
    ]

    DateParsingService service = new DateParsingService()
    LocalDate today = LocalDate.of(2026, 7, 6)

    void "heute resolves to today and is stripped"() {
        when:
        def result = service.parse("Müll rausbringen heute", today)

        then:
        result.date == today
        result.title == "Müll rausbringen"
    }

    void "morgen resolves to today plus one day"() {
        when:
        def result = service.parse("Müll rausbringen morgen", today)

        then:
        result.date == today.plusDays(1)
        result.title == "Müll rausbringen"
    }

    void "übermorgen resolves to today plus two days and is not confused with morgen"() {
        when:
        def result = service.parse("Müll rausbringen übermorgen", today)

        then:
        result.date == today.plusDays(2)
        result.title == "Müll rausbringen"
    }

    void "MORGEN in upper case is still recognized"() {
        when:
        def result = service.parse("Müll rausbringen MORGEN", today)

        then:
        result.date == today.plusDays(1)
    }

    void "Morgenlauf is not mistaken for morgen (whole-word match only)"() {
        when:
        def result = service.parse("Morgenlauf vorbereiten", today)

        then:
        result.date == null
        result.title == "Morgenlauf vorbereiten"
    }

    void "Montagsmeeting is not mistaken for a weekday phrase (compound word)"() {
        when:
        def result = service.parse("Montagsmeeting vorbereiten", today)

        then:
        result.date == null
        result.title == "Montagsmeeting vorbereiten"
    }

    void "a weekday name matching today's own weekday resolves to next week, not today"() {
        given:
        String name = GERMAN_NAME[today.dayOfWeek]

        when:
        def result = service.parse("Termin ${name}".toString(), today)

        then:
        result.date == today.plusDays(7)
        result.title == "Termin"
    }

    void "a weekday name resolves to the next upcoming occurrence of that weekday"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Termin ${name}".toString(), today)

        then:
        result.date == target
    }

    void "bis Freitag strips both 'bis' and the weekday name"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Steuererklärung abgeben bis ${name}".toString(), today)

        then:
        result.date == target
        result.title == "Steuererklärung abgeben"
    }

    void "am Montag strips both 'am' and the weekday name"() {
        given:
        LocalDate target = today.plusDays(2)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Zahnarzt am ${name}".toString(), today)

        then:
        result.date == target
        result.title == "Zahnarzt"
    }

    void "in 3 Tagen resolves to today plus three days"() {
        when:
        def result = service.parse("Paket abholen in 3 Tagen", today)

        then:
        result.date == today.plusDays(3)
        result.title == "Paket abholen"
    }

    void "in einem Tag resolves to today plus one day"() {
        when:
        def result = service.parse("Paket abholen in einem Tag", today)

        then:
        result.date == today.plusDays(1)
        result.title == "Paket abholen"
    }

    void "in einer Woche resolves to today plus seven days"() {
        when:
        def result = service.parse("Paket abholen in einer Woche", today)

        then:
        result.date == today.plusDays(7)
        result.title == "Paket abholen"
    }

    void "in 2 Wochen resolves to today plus fourteen days"() {
        when:
        def result = service.parse("Paket abholen in 2 Wochen", today)

        then:
        result.date == today.plusDays(14)
        result.title == "Paket abholen"
    }

    void "in 0 Tagen resolves to today (no special-casing needed)"() {
        when:
        def result = service.parse("Paket abholen in 0 Tagen", today)

        then:
        result.date == today
        result.title == "Paket abholen"
    }

    void "no recognized phrase leaves date null and title unchanged"() {
        when:
        def result = service.parse("Einfach nur ein Titel", today)

        then:
        result.date == null
        result.title == "Einfach nur ein Titel"
    }

    void "when the whole text is just the date phrase, the title is kept instead of left blank"() {
        when:
        def result = service.parse("morgen", today)

        then:
        result.date == today.plusDays(1)
        result.title == "morgen"
    }

    void "the leftmost of two phrases wins"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Termin ${name} oder übermorgen".toString(), today)

        then:
        result.date == target
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "taskboard.DateParsingServiceSpec"`
Expected: compilation FAILURE — `DateParsingService` and `ParsedTitle` don't exist yet.

- [ ] **Step 3: Implement `ParsedTitle.groovy`**

Create `src/main/groovy/taskboard/ParsedTitle.groovy`:

```groovy
package taskboard

import java.time.LocalDate

/** Result of DateParsingService.parse(): the extracted date (null if no
 *  recognized phrase was found) and the title with that phrase removed
 *  (unchanged if nothing was found, or if removing it would leave a blank
 *  title -- see DateParsingService for why). */
class ParsedTitle {
    LocalDate date
    String title
}
```

- [ ] **Step 4: Implement `DateParsingService.groovy`**

Create `grails-app/services/taskboard/DateParsingService.groovy`:

```groovy
package taskboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Extracts a German date phrase from free-form quick-add text (see design
 * spec docs/superpowers/specs/2026-07-03-family-taskboard-date-parsing-design.md).
 * Deterministic: takes `today` as a parameter rather than calling
 * LocalDate.now() internally, same pattern as UrgencyService.colorFor(task, today),
 * so callers/tests never depend on the real clock.
 *
 * Recognizes (case-insensitive, whole-word only via \b, with
 * UNICODE_CHARACTER_CLASS so ü/ö/ä count as word characters for boundary
 * purposes -- without it, Java's default \w/\b would misjudge the boundary
 * right before "übermorgen", since "ü" isn't in the default ASCII \w class):
 *   heute, morgen, übermorgen
 *   weekday names (Montag..Sonntag, plus the "-s" adverbial form e.g. montags) --
 *     always the NEXT occurrence strictly after `today`, even if today already is
 *     that weekday (then: next week, not today)
 *   "in N Tagen" / "in einem Tag", "in N Wochen" / "in einer Woche"
 *
 * If more than one phrase appears, the leftmost match wins (Matcher.find() on a
 * single combined alternation already returns the leftmost starting match).
 * The matched phrase (plus an immediately preceding "bis"/"am"/"in", if adjacent)
 * is removed from the title -- unless doing so would leave the title blank
 * (Task.title has blank:false), in which case the full original text is kept
 * as the title while still returning the parsed date.
 */
class DateParsingService {

    private static final Map<String, DayOfWeek> WEEKDAYS = [
        montag: DayOfWeek.MONDAY, dienstag: DayOfWeek.TUESDAY,
        mittwoch: DayOfWeek.WEDNESDAY, donnerstag: DayOfWeek.THURSDAY,
        freitag: DayOfWeek.FRIDAY, samstag: DayOfWeek.SATURDAY,
        sonntag: DayOfWeek.SUNDAY
    ]

    private static final Pattern PATTERN = Pattern.compile(
        '\\b(?<heute>heute)\\b' +
        '|\\b(?<uebermorgen>übermorgen)\\b' +
        '|\\b(?<morgen>morgen)\\b' +
        '|\\b(?<weekday>montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)s?\\b' +
        '|\\bin\\s+(?<reldays>\\d+)\\s+tag(?:en)?\\b' +
        '|\\b(?<eintag>in\\s+einem\\s+tag)\\b' +
        '|\\bin\\s+(?<relweeks>\\d+)\\s+woche(?:n)?\\b' +
        '|\\b(?<eineWoche>in\\s+einer\\s+woche)\\b',
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    )

    private static final Pattern LEADING_PREPOSITION =
        Pattern.compile('(?i)(?:bis|am|in)\\s+$')

    ParsedTitle parse(String title, LocalDate today) {
        Matcher m = PATTERN.matcher(title)
        if (!m.find()) {
            return new ParsedTitle(date: null, title: title)
        }

        LocalDate date
        if (m.group('heute')) {
            date = today
        } else if (m.group('morgen')) {
            date = today.plusDays(1)
        } else if (m.group('uebermorgen')) {
            date = today.plusDays(2)
        } else if (m.group('weekday')) {
            date = nextWeekday(today, WEEKDAYS[m.group('weekday').toLowerCase()])
        } else if (m.group('reldays') != null) {
            date = today.plusDays(m.group('reldays') as long)
        } else if (m.group('eintag')) {
            date = today.plusDays(1)
        } else if (m.group('relweeks') != null) {
            date = today.plusWeeks(m.group('relweeks') as long)
        } else {
            date = today.plusWeeks(1) // eineWoche
        }

        int start = m.start()
        int end = m.end()
        Matcher prep = LEADING_PREPOSITION.matcher(title.substring(0, start))
        if (prep.find()) {
            start = prep.start()
        }

        String stripped = (title.substring(0, start) + title.substring(end))
            .replaceAll(/\s{2,}/, ' ').trim()

        new ParsedTitle(date: date, title: stripped ?: title)
    }

    private static LocalDate nextWeekday(LocalDate today, DayOfWeek target) {
        int daysToAdd = (target.value - today.dayOfWeek.value + 7) % 7
        if (daysToAdd == 0) daysToAdd = 7
        today.plusDays(daysToAdd)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "taskboard.DateParsingServiceSpec"`
Expected: PASS (18 tests).

- [ ] **Step 6: Run the full unit test suite to confirm nothing broke**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all unit specs pass, including the pre-existing `TaskSpec`, `UserSpec`, `UrgencyServiceSpec`, `PushServicePayloadSpec`, `ProjectSpec`.

- [ ] **Step 7: Commit**

```bash
git add src/main/groovy/taskboard/ParsedTitle.groovy grails-app/services/taskboard/DateParsingService.groovy src/test/groovy/taskboard/DateParsingServiceSpec.groovy
git commit -m "feat: add DateParsingService for German date phrases in quick-add text"
```

---

### Task 2: Wire `DateParsingService` into `TaskService.createTask()` and both quick-add controllers

**Files:**
- Modify: `grails-app/services/taskboard/TaskService.groovy`
- Modify: `grails-app/controllers/taskboard/ApiTaskController.groovy`
- Modify: `grails-app/controllers/taskboard/TaskController.groovy:37-48` (the `quickAdd()` action)
- Modify: `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`
- Modify: `src/integration-test/groovy/taskboard/ApiTaskControllerIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `DateParsingService.parse(String title, LocalDate today) -> ParsedTitle` (Task 1).
- Produces: `TaskService.createTask(Map params, User creator)` — unchanged signature, but when `params.dueDate` is absent/null, it now parses `params.title` for a date phrase and uses the parsed date + stripped title instead of defaulting straight to today.

- [ ] **Step 1: Write the failing integration tests**

Append these test methods to `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`, just before the final closing `}` of the class:

```groovy
    void "createTask with no explicit dueDate parses a date phrase from the title"() {
        given:
        def u = new User(username: "date-u1", password: "p",
            displayName: "U", apiToken: "dateu1").save(flush: true)

        when:
        def t = taskService.createTask([title: "Müll rausbringen morgen",
            priority: Priority.LOW], u)

        then:
        t.dueDate == LocalDate.now().plusDays(1)
        t.title == "Müll rausbringen"
    }

    void "createTask with an explicit dueDate ignores any date phrase in the title"() {
        given:
        def u = new User(username: "date-u2", password: "p",
            displayName: "U", apiToken: "dateu2").save(flush: true)
        LocalDate explicitDate = LocalDate.now().plusDays(20)

        when:
        def t = taskService.createTask([title: "Task bis Freitag",
            dueDate: explicitDate, priority: Priority.LOW], u)

        then:
        t.dueDate == explicitDate
        t.title == "Task bis Freitag"
    }

    void "createTask with no date phrase and no explicit dueDate defaults to today"() {
        given:
        def u = new User(username: "date-u3", password: "p",
            displayName: "U", apiToken: "dateu3").save(flush: true)

        when:
        def t = taskService.createTask([title: "Nur ein Titel",
            priority: Priority.LOW], u)

        then:
        t.dueDate == LocalDate.now()
        t.title == "Nur ein Titel"
    }
```

Append this test method to `src/integration-test/groovy/taskboard/ApiTaskControllerIntegrationSpec.groovy`, just before the final closing `}` of the class, and add `import java.time.DayOfWeek` and `import java.time.LocalDate` to its import list:

```groovy
    void "quick-add parses 'bis <weekday>' from the dictated text and strips it from the title"() {
        given:
        User.withTransaction {
            new User(username: "api-date", password: "p", displayName: "ApiDate",
                     apiToken: "api-date-token").save(flush: true, failOnError: true)
        }
        LocalDate today = LocalDate.now()
        int daysToAdd = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
        if (daysToAdd == 0) daysToAdd = 7
        LocalDate expectedDueDate = today.plusDays(daysToAdd)

        def conn = new URL("http://localhost:${serverPort}/api/tasks/quick").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer api-date-token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.withWriter {
            it << JsonOutput.toJson([text: "Steuererklärung abgeben bis Freitag"])
        }

        expect:
        conn.responseCode == 201
        def body = new groovy.json.JsonSlurper().parse(conn.inputStream)
        body.title == "Steuererklärung abgeben"
        body.dueDate == expectedDueDate.toString()

        cleanup:
        Task.withTransaction {
            Task.findAllByTitle("Steuererklärung abgeben")*.delete(flush: true)
        }
        User.withTransaction {
            User.findByUsername("api-date")?.delete(flush: true)
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec" --tests "taskboard.ApiTaskControllerIntegrationSpec"`
Expected: FAILURE — `TaskService.createTask` doesn't parse yet (all three new `TaskServiceIntegrationSpec` cases fail on `t.dueDate`/`t.title`), and the new `ApiTaskControllerIntegrationSpec` case fails (`title`/`dueDate` in the response won't match, since `ApiTaskController.quick()` still passes an explicit `dueDate: LocalDate.now()` that defeats parsing).

- [ ] **Step 3: Update `TaskService.groovy`**

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
```

- [ ] **Step 4: Update `ApiTaskController.groovy`**

Replace the full contents of `grails-app/controllers/taskboard/ApiTaskController.groovy` with:

```groovy
package taskboard

import grails.converters.JSON

/**
 * Stateless REST quick-add for Apple Shortcuts (Siri, Watch, Back Tap, Action
 * Button, Lock Screen widget) -- see docs/apple-shortcut.md. Mapped to
 * POST /api/tasks/quick in UrlMappings.groovy, ahead of the generic catch-all
 * route. Auth is a per-user Bearer token (User.apiToken), not a session, so
 * this path is exempt from CSRF and not covered by formLogin (see the
 * /api/** entries in SecurityConfig.groovy).
 */
class ApiTaskController {

    TaskService taskService

    /** 201 + created task JSON, 401 for a missing/unknown token, 422 for empty/missing text.
     *  No dueDate is passed here -- TaskService.createTask() parses a date phrase (e.g.
     *  "bis Freitag") out of the dictated text itself, defaulting to today if none is found. */
    def quick() {
        String auth = request.getHeader('Authorization')
        String token = auth?.startsWith('Bearer ') ? auth.substring(7) : null
        User user = token ? User.findByApiToken(token) : null
        if (!user) {
            render status: 401, contentType: 'application/json',
                text: ([error: 'invalid token'] as JSON).toString()
            return
        }
        def body = request.JSON
        String text = body?.text?.toString()?.trim()
        if (!text) {
            render status: 422, contentType: 'application/json',
                text: ([error: 'empty text'] as JSON).toString()
            return
        }
        def task = taskService.createTask([title: text, priority: Priority.MEDIUM], user)
        render status: 201, contentType: 'application/json',
            text: ([id: task.id, title: task.title,
                    dueDate: task.dueDate.toString()] as JSON).toString()
    }
}
```

- [ ] **Step 5: Update `TaskController.quickAdd()`**

In `grails-app/controllers/taskboard/TaskController.groovy`, replace:

```groovy
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
```

with:

```groovy
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
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec" --tests "taskboard.ApiTaskControllerIntegrationSpec"`
Expected: PASS (13 tests in `TaskServiceIntegrationSpec`, 3 in `ApiTaskControllerIntegrationSpec`).

- [ ] **Step 7: Run the full suite to confirm no regressions**

Run: `./gradlew test integrationTest`
Expected: BUILD SUCCESSFUL — every unit and integration spec passes, including all pre-existing Phase 1/2 specs (`TaskControllerSessionFlowIntegrationSpec`, `ProjectFlowIntegrationSpec`, etc. — none of them pass an explicit `dueDate` of `null` in a way the new code would mishandle, and every existing test that DOES pass an explicit `dueDate` must still see it win over parsing).

- [ ] **Step 8: Manually verify via `bootRun`**

Run: `./gradlew bootRun -Dgrails.env=test`, then in a browser:
1. Log in as `lars`/`changeme`.
2. In quick-add, type "Zahnarzt bis Freitag" and submit.
3. Confirm the new card shows title "Zahnarzt" (not "Zahnarzt bis Freitag") and the due date shown is the upcoming Friday.
4. Type "Wäsche waschen" (no date phrase) and submit — confirm it defaults to today, exactly as before.

- [ ] **Step 9: Commit**

```bash
git add grails-app/services/taskboard/TaskService.groovy grails-app/controllers/taskboard/ApiTaskController.groovy grails-app/controllers/taskboard/TaskController.groovy src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy src/integration-test/groovy/taskboard/ApiTaskControllerIntegrationSpec.groovy
git commit -m "feat: parse German date phrases out of quick-add text"
```
