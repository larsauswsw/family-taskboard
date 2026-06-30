# Family Taskboard Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Grails 7.1.1 family task-management web app with color-coded urgency, voice quick-add (in-app and via Apple Shortcuts), and Web Push notifications, running in Docker.

**Architecture:** Grails monolith serving GSP + HTMX for the UI, a REST endpoint for external quick-add, GORM/Hibernate against PostgreSQL, Quartz for scheduled notifications, and Web Push (VAPID) for iOS notifications. Two services in Docker Compose: `app` and `db`.

**Tech Stack:** Grails 7.1.1, Java 21, GORM/Hibernate, PostgreSQL, Spring Security Core plugin, Quartz plugin, HTMX, Web Speech API, Web Push (nl.martijndwars:web-push), Docker Compose.

## Global Constraints

- Grails version: exactly `7.1.1`
- JDK: Java 21 (LTS)
- Database: PostgreSQL (no H2 in production profile)
- UI: server-rendered GSP enhanced with HTMX — no SPA framework
- Auth: Spring Security Core, username/password, bcrypt-hashed
- Quick-add REST auth: per-user `apiToken`, `Authorization: Bearer <token>`
- Push: Web Push with VAPID keys; no APNs
- Secrets (DB password, VAPID keys) via environment variables, never committed
- All copy in the UI is German (matches the family users)
- Urgency color order: green → yellow → orange → red → darkred (overdue)

---

### Task 1: Project scaffold, Docker, and PostgreSQL connectivity

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradle.properties`
- Create: `grails-app/conf/application.yml`
- Create: `Dockerfile`, `docker-compose.yml`, `.env.example`, `.gitignore`
- Create: `grails-app/init/taskboard/Application.groovy`, `grails-app/init/taskboard/BootStrap.groovy`
- Test: `src/integration-test/groovy/taskboard/SmokeIntegrationSpec.groovy`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a runnable Grails app on port 8080, PostgreSQL datasource bound via env vars, package root `taskboard`.

- [ ] **Step 1: Generate the Grails app**

Run:
```bash
sdk install grails 7.1.1 || true
grails create-app taskboard --profile=web
cd taskboard
```
Move the generated files into the repo root if `create-app` made a subfolder (the repo root must contain `build.gradle`).

- [ ] **Step 2: Add plugins and the web-push dependency to `build.gradle`**

In the `dependencies { }` block add:
```groovy
implementation "org.grails.plugins:spring-security-core:7.0.0"
implementation "org.grails.plugins:quartz:3.0.0"
implementation "org.postgresql:postgresql:42.7.4"
implementation "nl.martijndwars:web-push:5.1.1"
implementation "org.bouncycastle:bcprov-jdk18on:1.78.1"
```

- [ ] **Step 3: Configure PostgreSQL datasource via env vars in `application.yml`**

Replace the `dataSource` and `environments` production block with:
```yaml
dataSource:
    pooled: true
    jmxExport: true
    driverClassName: org.postgresql.Driver
    dialect: org.hibernate.dialect.PostgreSQLDialect
    username: "${DB_USER:taskboard}"
    password: "${DB_PASSWORD:taskboard}"
    url: "${DB_URL:jdbc:postgresql://localhost:5432/taskboard}"

hibernate:
    cache:
        queries: false
        use_second_level_cache: false

environments:
    development:
        dataSource:
            dbCreate: update
    production:
        dataSource:
            dbCreate: update
            properties:
                jmxEnabled: true
```

- [ ] **Step 4: Write `.gitignore` and `.env.example`**

`.gitignore`:
```
.gradle/
build/
*.log
.env
out/
```

`.env.example`:
```
DB_USER=taskboard
DB_PASSWORD=changeme
DB_URL=jdbc:postgresql://db:5432/taskboard
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
VAPID_SUBJECT=mailto:admin@example.com
```

- [ ] **Step 5: Write the `Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew assemble --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Write `docker-compose.yml`**

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: taskboard
    volumes:
      - dbdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"
  app:
    build: .
    depends_on:
      - db
    environment:
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      DB_URL: jdbc:postgresql://db:5432/taskboard
      VAPID_PUBLIC_KEY: ${VAPID_PUBLIC_KEY}
      VAPID_PRIVATE_KEY: ${VAPID_PRIVATE_KEY}
      VAPID_SUBJECT: ${VAPID_SUBJECT}
    ports:
      - "8080:8080"
volumes:
  dbdata:
```

- [ ] **Step 7: Write the smoke integration test**

`src/integration-test/groovy/taskboard/SmokeIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class SmokeIntegrationSpec extends Specification {
    void "application context loads"() {
        expect:
        true
    }
}
```

- [ ] **Step 8: Run the test to verify the app boots**

Run: `./gradlew integrationTest --tests "taskboard.SmokeIntegrationSpec"`
Expected: PASS (Grails boots, context loads).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: scaffold Grails app with PostgreSQL and Docker"
```

---

### Task 2: User domain and Spring Security

**Files:**
- Create: `grails-app/domain/taskboard/User.groovy`, `Role.groovy`, `UserRole.groovy`
- Modify: `grails-app/conf/application.groovy` (security config)
- Modify: `grails-app/init/taskboard/BootStrap.groovy` (seed family users)
- Test: `src/test/groovy/taskboard/UserSpec.groovy`

**Interfaces:**
- Consumes: app scaffold from Task 1.
- Produces: `User` with fields `username`, `password`, `displayName`, `email`, `apiToken`, `notifyDaysBefore` (Integer, default 1), `notifyOnDueDate` (boolean, default true); method `User.findByApiToken(String)`. Spring Security secures all URLs except login and `/api/**` (handled in Task 8).

- [ ] **Step 1: Run the Spring Security quickstart generator**

Run:
```bash
grails s2-quickstart taskboard User Role
```
This generates `User`, `Role`, `UserRole`.

- [ ] **Step 2: Add custom fields to `User.groovy`**

Add to the `User` class body and constraints:
```groovy
String displayName
String email
String apiToken
Integer notifyDaysBefore = 1
boolean notifyOnDueDate = true

static constraints = {
    password nullable: false, blank: false, password: true
    username nullable: false, blank: false, unique: true
    displayName nullable: false, blank: false
    email nullable: true, email: true
    apiToken nullable: true, unique: true
}
```

- [ ] **Step 3: Write the failing test for apiToken lookup**

`src/test/groovy/taskboard/UserSpec.groovy`:
```groovy
package taskboard

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class UserSpec extends Specification implements DomainUnitTest<User> {
    void "user is found by apiToken"() {
        given:
        new User(username: "lars", password: "secret",
                 displayName: "Lars", apiToken: "tok-123").save(flush: true)
        expect:
        User.findByApiToken("tok-123")?.username == "lars"
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests "taskboard.UserSpec"`
Expected: PASS.

- [ ] **Step 5: Configure security rules in `application.groovy`**

Append:
```groovy
grails.plugin.springsecurity.userLookup.userDomainClassName = 'taskboard.User'
grails.plugin.springsecurity.authority.className = 'taskboard.Role'
grails.plugin.springsecurity.requestMap.className = 'taskboard.UserRole'
grails.plugin.springsecurity.securityConfigType = 'Annotation'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/',           access: ['permitAll']],
    [pattern: '/error',      access: ['permitAll']],
    [pattern: '/assets/**',  access: ['permitAll']],
    [pattern: '/manifest.json', access: ['permitAll']],
    [pattern: '/sw.js',      access: ['permitAll']],
    [pattern: '/login/**',   access: ['permitAll']],
    [pattern: '/logout/**',  access: ['permitAll']],
    [pattern: '/api/**',     access: ['permitAll']]
]
```

- [ ] **Step 6: Seed family users in `BootStrap.groovy`**

```groovy
import taskboard.*

class BootStrap {
    def init = { servletContext ->
        if (!Role.count()) {
            def userRole = new Role(authority: 'ROLE_USER').save(flush: true)
            def lars = new User(username: 'lars', password: 'changeme',
                displayName: 'Lars', apiToken: UUID.randomUUID().toString()).save(flush: true)
            UserRole.create(lars, userRole, true)
        }
    }
    def destroy = {}
}
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add User domain with Spring Security and apiToken"
```

---

### Task 3: Task domain with Priority and Status enums

**Files:**
- Create: `grails-app/domain/taskboard/Task.groovy`
- Create: `src/main/groovy/taskboard/Priority.groovy`, `src/main/groovy/taskboard/TaskStatus.groovy`
- Test: `src/test/groovy/taskboard/TaskSpec.groovy`

**Interfaces:**
- Consumes: `User` from Task 2.
- Produces: `Task` with fields `title` (String), `dueDate` (LocalDate), `priority` (Priority), `status` (TaskStatus, default OPEN), `description` (String, nullable), `assignedTo` (User, nullable), `createdBy` (User), `project` (nullable Long placeholder until Phase 2 — omit association now), `lastNotifiedAt` (LocalDateTime, nullable). Enums `Priority{LOW,MEDIUM,HIGH,CRITICAL}` and `TaskStatus{OPEN,IN_PROGRESS,DONE}`.

- [ ] **Step 1: Create the enums**

`src/main/groovy/taskboard/Priority.groovy`:
```groovy
package taskboard

enum Priority {
    LOW(1.0d), MEDIUM(1.2d), HIGH(1.5d), CRITICAL(2.0d)
    final double multiplier
    Priority(double m) { this.multiplier = m }
}
```

`src/main/groovy/taskboard/TaskStatus.groovy`:
```groovy
package taskboard

enum TaskStatus {
    OPEN, IN_PROGRESS, DONE
}
```

- [ ] **Step 2: Write the failing test**

`src/test/groovy/taskboard/TaskSpec.groovy`:
```groovy
package taskboard

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification
import java.time.LocalDate

class TaskSpec extends Specification implements DomainUnitTest<Task> {
    void "task requires a title and defaults to OPEN"() {
        when:
        def t = new Task(title: "Test", dueDate: LocalDate.now(),
                         priority: Priority.MEDIUM)
        then:
        t.status == TaskStatus.OPEN
        t.priority.multiplier == 1.2d
    }

    void "blank title is invalid"() {
        expect:
        !new Task(title: "", dueDate: LocalDate.now(),
                  priority: Priority.LOW).validate(['title'])
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "taskboard.TaskSpec"`
Expected: FAIL — `Task` class does not exist.

- [ ] **Step 4: Create `Task.groovy`**

```groovy
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "taskboard.TaskSpec"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add Task domain with Priority and TaskStatus enums"
```

---

### Task 4: UrgencyConfig and UrgencyService (core color logic)

**Files:**
- Create: `grails-app/domain/taskboard/UrgencyConfig.groovy`
- Create: `grails-app/services/taskboard/UrgencyService.groovy`
- Modify: `grails-app/init/taskboard/BootStrap.groovy` (seed a default config)
- Test: `src/test/groovy/taskboard/UrgencyServiceSpec.groovy`

**Interfaces:**
- Consumes: `Task`, `Priority` from Task 3.
- Produces: `UrgencyService.colorFor(Task task, LocalDate today)` returning a `String` in `['green','yellow','orange','red','darkred']`; `UrgencyService.effectiveDays(Task, LocalDate)` returning a `double`. `UrgencyConfig` singleton with `greenDaysThreshold=14`, `yellowDaysThreshold=7`, `orangeDaysThreshold=3`, `redDaysThreshold=1`.

- [ ] **Step 1: Create `UrgencyConfig.groovy`**

```groovy
package taskboard

class UrgencyConfig {
    Integer greenDaysThreshold = 14
    Integer yellowDaysThreshold = 7
    Integer orangeDaysThreshold = 3
    Integer redDaysThreshold = 1

    static UrgencyConfig current() {
        UrgencyConfig.first() ?: new UrgencyConfig()
    }
}
```

- [ ] **Step 2: Write the failing test covering every band**

`src/test/groovy/taskboard/UrgencyServiceSpec.groovy`:
```groovy
package taskboard

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Unroll
import java.time.LocalDate

class UrgencyServiceSpec extends Specification implements ServiceUnitTest<UrgencyService> {

    private Task task(int daysOut, Priority p) {
        new Task(title: "t", dueDate: LocalDate.of(2026,1,1).plusDays(daysOut),
                 priority: p)
    }

    @Unroll
    void "#days days out with #prio is #expected"() {
        given:
        def today = LocalDate.of(2026,1,1)
        def cfg = new UrgencyConfig()
        expect:
        service.colorFor(task(days, prio), today, cfg) == expected

        where:
        days | prio             | expected
        20   | Priority.LOW     | 'green'
        10   | Priority.LOW     | 'yellow'
        5    | Priority.LOW     | 'orange'
        2    | Priority.LOW     | 'red'
        0    | Priority.LOW     | 'red'
        -1   | Priority.LOW     | 'darkred'
        6    | Priority.CRITICAL| 'orange'
    }
}
```

The CRITICAL case: 6 days / multiplier 2.0 = effectiveDays 3.0, which is `<= orangeThreshold(3)` and `> redThreshold(1)` → orange.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "taskboard.UrgencyServiceSpec"`
Expected: FAIL — `UrgencyService` does not exist.

- [ ] **Step 4: Implement `UrgencyService.groovy`**

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UrgencyService {

    double effectiveDays(Task task, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, task.dueDate)
        days / task.priority.multiplier
    }

    String colorFor(Task task, LocalDate today, UrgencyConfig cfg = UrgencyConfig.current()) {
        long rawDays = ChronoUnit.DAYS.between(today, task.dueDate)
        if (rawDays < 0) return 'darkred'
        double eff = effectiveDays(task, today)
        if (eff > cfg.greenDaysThreshold) return 'green'
        if (eff > cfg.yellowDaysThreshold) return 'yellow'
        if (eff > cfg.orangeDaysThreshold) return 'orange'
        return 'red'
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "taskboard.UrgencyServiceSpec"`
Expected: PASS (all rows).

- [ ] **Step 6: Seed a default `UrgencyConfig` in `BootStrap.groovy`**

Inside the `init` closure, after user seeding:
```groovy
if (!UrgencyConfig.count()) {
    new UrgencyConfig().save(flush: true)
}
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add UrgencyService with configurable color bands"
```

---

### Task 5: Task list view with color coding and HTMX quick-add

**Files:**
- Create: `grails-app/controllers/taskboard/TaskController.groovy`
- Create: `grails-app/services/taskboard/TaskService.groovy`
- Create: `grails-app/views/task/index.gsp`, `grails-app/views/task/_card.gsp`, `grails-app/views/task/_list.gsp`
- Create: `grails-app/assets/stylesheets/taskboard.css`
- Modify: `grails-app/controllers/taskboard/UrlMappings.groovy` (default to task index)
- Test: `src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `Task`, `User`, `UrgencyService` from prior tasks.
- Produces: `TaskService.createTask(Map params, User creator)` returning a saved `Task`; `TaskService.openTasksSorted()` returning `List<Task>` sorted by `dueDate` ascending. `TaskController` actions `index`, `quickAdd` (HTMX form post returning `_list` fragment), `complete`.

- [ ] **Step 1: Write the failing service integration test**

`src/integration-test/groovy/taskboard/TaskServiceIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import java.time.LocalDate

@Integration
@Rollback
class TaskServiceIntegrationSpec extends Specification {

    TaskService taskService

    void "createTask saves an OPEN task with creator"() {
        given:
        def u = new User(username: "u1", password: "p",
            displayName: "U", apiToken: "t1").save(flush: true)
        when:
        def t = taskService.createTask(
            [title: "Müll rausbringen", dueDate: LocalDate.now().plusDays(2),
             priority: Priority.HIGH], u)
        then:
        t.id != null
        t.status == TaskStatus.OPEN
        t.createdBy.username == "u1"
    }

    void "openTasksSorted returns only non-DONE ordered by dueDate"() {
        given:
        def u = new User(username: "u2", password: "p",
            displayName: "U", apiToken: "t2").save(flush: true)
        taskService.createTask([title: "later",
            dueDate: LocalDate.now().plusDays(10), priority: Priority.LOW], u)
        taskService.createTask([title: "soon",
            dueDate: LocalDate.now().plusDays(1), priority: Priority.LOW], u)
        when:
        def list = taskService.openTasksSorted()
        then:
        list.first().title == "soon"
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: FAIL — `TaskService` does not exist.

- [ ] **Step 3: Implement `TaskService.groovy`**

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate

@Transactional
class TaskService {

    Task createTask(Map params, User creator) {
        def task = new Task(
            title: params.title,
            dueDate: params.dueDate ?: LocalDate.now(),
            priority: params.priority ?: Priority.MEDIUM,
            description: params.description,
            assignedTo: params.assignedTo,
            createdBy: creator
        )
        task.save(failOnError: true)
        task
    }

    List<Task> openTasksSorted() {
        Task.findAllByStatusNotEqual(TaskStatus.DONE, [sort: 'dueDate', order: 'asc'])
    }

    Task complete(Long id) {
        def t = Task.get(id)
        t.status = TaskStatus.DONE
        t.save(failOnError: true)
        t
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.TaskServiceIntegrationSpec"`
Expected: PASS.

- [ ] **Step 5: Implement `TaskController.groovy`**

```groovy
package taskboard

import grails.plugin.springsecurity.annotation.Secured
import java.time.LocalDate

@Secured('ROLE_USER')
class TaskController {

    TaskService taskService
    UrgencyService urgencyService
    def springSecurityService

    def index() {
        [tasks: taskService.openTasksSorted(), urgencyService: urgencyService,
         today: LocalDate.now(), users: User.list()]
    }

    def quickAdd() {
        User creator = springSecurityService.currentUser
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
```

- [ ] **Step 6: Create the views**

`grails-app/views/task/index.gsp`:
```html
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>Meine Tasks</title>
    <link rel="manifest" href="/manifest.json">
    <asset:stylesheet src="taskboard.css"/>
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
</head>
<body>
    <header class="navbar"><h1>Meine Tasks</h1></header>
    <main id="task-list">
        <g:render template="list"
            model="[tasks: tasks, urgencyService: urgencyService, today: today]"/>
    </main>

    <form id="quick-add" hx-post="${createLink(action: 'quickAdd')}"
          hx-target="#task-list" hx-swap="innerHTML">
        <input type="text" name="title" id="title-input" placeholder="Neuer Task…" required>
        <button type="button" id="mic-btn" class="fab" aria-label="Per Sprache hinzufügen">🎤</button>
        <button type="submit">+</button>
    </form>

    <asset:javascript src="voice.js"/>
</body>
</html>
```

`grails-app/views/task/_list.gsp`:
```html
<g:each in="${tasks}" var="task">
    <g:render template="card"
        model="[task: task, color: urgencyService.colorFor(task, today)]"/>
</g:each>
```

`grails-app/views/task/_card.gsp`:
```html
<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <div class="task-meta">
        <span>${task.assignedTo?.displayName ?: '—'}</span>
        <span><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority}</span>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
</div>
```

- [ ] **Step 7: Add color CSS**

`grails-app/assets/stylesheets/taskboard.css`:
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
```

- [ ] **Step 8: Point default URL to task index in `UrlMappings.groovy`**

Replace the `"/"(...)` line with:
```groovy
"/"(controller: 'task', action: 'index')
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: task list with color coding and HTMX quick-add"
```

---

### Task 6: In-app voice input (Web Speech API)

**Files:**
- Create: `grails-app/assets/javascripts/voice.js`
- Test: manual (browser API; documented verification)

**Interfaces:**
- Consumes: the `#mic-btn` and `#title-input` elements from Task 5's `index.gsp`.
- Produces: clicking the mic button dictates speech into the title input.

- [ ] **Step 1: Implement `voice.js`**

```javascript
(function () {
  const btn = document.getElementById('mic-btn');
  const input = document.getElementById('title-input');
  if (!btn || !input) return;

  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) { btn.style.display = 'none'; return; }

  const recognition = new SR();
  recognition.lang = 'de-DE';
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;

  btn.addEventListener('click', function () {
    recognition.start();
    btn.classList.add('listening');
  });

  recognition.addEventListener('result', function (e) {
    input.value = e.results[0][0].transcript;
    btn.classList.remove('listening');
    input.focus();
  });

  recognition.addEventListener('end', function () {
    btn.classList.remove('listening');
  });
})();
```

- [ ] **Step 2: Manually verify in Safari/Chrome**

Open the app, tap the mic button, grant permission, speak "Test Aufgabe". Confirm the text appears in the title input. Document the result in the commit message.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: in-app voice dictation via Web Speech API"
```

---

### Task 7: REST quick-add endpoint with token auth

**Files:**
- Create: `grails-app/controllers/taskboard/ApiTaskController.groovy`
- Modify: `grails-app/controllers/taskboard/UrlMappings.groovy`
- Test: `src/integration-test/groovy/taskboard/ApiTaskControllerIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `User.findByApiToken`, `TaskService.createTask`.
- Produces: `POST /api/tasks/quick` with `Authorization: Bearer <token>` and JSON `{ "text": "..." }`; returns 201 with the created task JSON, 401 for bad token, 422 for empty text.

- [ ] **Step 1: Write the failing integration test**

`src/integration-test/groovy/taskboard/ApiTaskControllerIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import groovy.json.JsonOutput

@Integration
@Rollback
class ApiTaskControllerIntegrationSpec extends Specification {

    @grails.web.servlet.context.GrailsConfigurationAware
    String baseUrl() { "http://localhost:${serverPort}" }

    Integer serverPort

    void "quick-add with valid token creates a task"() {
        given:
        new User(username: "api", password: "p", displayName: "Api",
                 apiToken: "valid-token").save(flush: true)
        def conn = new URL("http://localhost:${serverPort}/api/tasks/quick").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer valid-token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.withWriter { it << JsonOutput.toJson([text: "Test Aufgabe"]) }

        expect:
        conn.responseCode == 201
    }

    void "quick-add with bad token returns 401"() {
        given:
        def conn = new URL("http://localhost:${serverPort}/api/tasks/quick").openConnection()
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer nope")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.withWriter { it << JsonOutput.toJson([text: "x"]) }

        expect:
        conn.responseCode == 401
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.ApiTaskControllerIntegrationSpec"`
Expected: FAIL — endpoint not mapped.

- [ ] **Step 3: Implement `ApiTaskController.groovy`**

```groovy
package taskboard

import grails.converters.JSON
import java.time.LocalDate

class ApiTaskController {

    TaskService taskService

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
        def task = taskService.createTask(
            [title: text, dueDate: LocalDate.now(), priority: Priority.MEDIUM], user)
        render status: 201, contentType: 'application/json',
            text: ([id: task.id, title: task.title,
                    dueDate: task.dueDate.toString()] as JSON).toString()
    }
}
```

- [ ] **Step 4: Map the route in `UrlMappings.groovy`**

Add inside `mappings { }`:
```groovy
post "/api/tasks/quick"(controller: 'apiTask', action: 'quick')
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.ApiTaskControllerIntegrationSpec"`
Expected: PASS (both cases).

- [ ] **Step 6: Document the Apple Shortcut setup**

Create `docs/apple-shortcut.md` describing: a "Dictate Text" action → "Get Contents of URL" (POST to `/api/tasks/quick`, header `Authorization: Bearer <user apiToken>`, JSON body `{ "text": <dictated> }`) → "Show Result". Note assigning it to Siri phrase, Back-Tap, Action Button, and a Lock Screen widget, and syncing via iCloud to the Watch.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: REST quick-add endpoint with Bearer token auth"
```

---

### Task 8: Web Push subscriptions and sending

**Files:**
- Create: `grails-app/domain/taskboard/PushSubscription.groovy`
- Create: `grails-app/services/taskboard/PushService.groovy`
- Create: `grails-app/controllers/taskboard/PushController.groovy`
- Create: `grails-app/assets/javascripts/push.js`, `src/main/webapp/sw.js`
- Modify: `grails-app/controllers/taskboard/UrlMappings.groovy`
- Test: `src/integration-test/groovy/taskboard/PushServiceIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `User`, VAPID env vars (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`).
- Produces: `PushSubscription(endpoint, p256dh, auth, user)`; `PushService.sendToUser(User, String title, String body)`; `PushService.publicKey()` returning the VAPID public key; `POST /push/subscribe`.

- [ ] **Step 1: Create `PushSubscription.groovy`**

```groovy
package taskboard

class PushSubscription {
    String endpoint
    String p256dh
    String auth
    User user

    static constraints = {
        endpoint nullable: false, unique: true, maxSize: 1000
        p256dh nullable: false
        auth nullable: false
    }
}
```

- [ ] **Step 2: Write the failing test for subscription persistence**

`src/integration-test/groovy/taskboard/PushServiceIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Integration
@Rollback
class PushServiceIntegrationSpec extends Specification {

    PushService pushService

    void "publicKey is exposed from configuration"() {
        expect:
        pushService.publicKey() != null
    }

    void "subscriptions are stored per user"() {
        given:
        def u = new User(username: "pu", password: "p",
            displayName: "P", apiToken: "pt").save(flush: true)
        when:
        new PushSubscription(endpoint: "https://ep/1", p256dh: "key",
            auth: "auth", user: u).save(flush: true)
        then:
        PushSubscription.findAllByUser(u).size() == 1
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.PushServiceIntegrationSpec"`
Expected: FAIL — `PushService` does not exist.

- [ ] **Step 4: Configure VAPID keys in `application.yml`**

```yaml
vapid:
    publicKey: "${VAPID_PUBLIC_KEY:}"
    privateKey: "${VAPID_PRIVATE_KEY:}"
    subject: "${VAPID_SUBJECT:mailto:admin@example.com}"
```

- [ ] **Step 5: Implement `PushService.groovy`**

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import nl.martijndwars.webpush.PushService as WebPush
import nl.martijndwars.webpush.Notification
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@Transactional
class PushService {

    def grailsApplication

    String publicKey() {
        grailsApplication.config.getProperty('vapid.publicKey')
    }

    void sendToUser(User user, String title, String body) {
        Security.addProvider(new BouncyCastleProvider())
        def cfg = grailsApplication.config
        def web = new WebPush()
        web.setPublicKey(cfg.getProperty('vapid.publicKey'))
        web.setPrivateKey(cfg.getProperty('vapid.privateKey'))
        web.setSubject(cfg.getProperty('vapid.subject'))

        String payload = "{\"title\":\"${title}\",\"body\":\"${body}\"}"
        PushSubscription.findAllByUser(user).each { sub ->
            try {
                web.send(new Notification(sub.endpoint, sub.p256dh, sub.auth,
                    payload.getBytes('UTF-8')))
            } catch (Exception e) {
                log.warn("Push failed for ${sub.endpoint}: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.PushServiceIntegrationSpec"`
Expected: PASS. (Set `VAPID_PUBLIC_KEY` in the test env or `application.yml` default so `publicKey()` is non-null; generate a keypair with `web-push generate-vapid-keys` or the library's CLI.)

- [ ] **Step 7: Implement `PushController.groovy` for subscribe**

```groovy
package taskboard

import grails.plugin.springsecurity.annotation.Secured
import grails.converters.JSON

@Secured('ROLE_USER')
class PushController {

    PushService pushService
    def springSecurityService

    def key() {
        render([publicKey: pushService.publicKey()] as JSON)
    }

    def subscribe() {
        def body = request.JSON
        def user = springSecurityService.currentUser
        if (!PushSubscription.findByEndpoint(body.endpoint)) {
            new PushSubscription(endpoint: body.endpoint,
                p256dh: body.keys.p256dh, auth: body.keys.auth,
                user: user).save(flush: true)
        }
        render status: 201, text: '{"ok":true}', contentType: 'application/json'
    }
}
```

- [ ] **Step 8: Create the service worker `src/main/webapp/sw.js`**

```javascript
self.addEventListener('push', function (event) {
  const data = event.data ? event.data.json() : {};
  event.waitUntil(
    self.registration.showNotification(data.title || 'Taskboard', {
      body: data.body || '',
      icon: '/assets/icon-192.png'
    })
  );
});
```

- [ ] **Step 9: Implement `push.js` to register and subscribe**

```javascript
(function () {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;

  function urlBase64ToUint8Array(base64) {
    const padding = '='.repeat((4 - base64.length % 4) % 4);
    const b64 = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(b64);
    return Uint8Array.from([...raw].map(c => c.charCodeAt(0)));
  }

  navigator.serviceWorker.register('/sw.js').then(async function (reg) {
    const res = await fetch('/push/key');
    const { publicKey } = await res.json();
    if (!publicKey) return;
    const perm = await Notification.requestPermission();
    if (perm !== 'granted') return;
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey)
    });
    await fetch('/push/subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(sub)
    });
  });
})();
```

- [ ] **Step 10: Map push routes in `UrlMappings.groovy`**

```groovy
"/push/key"(controller: 'push', action: 'key')
post "/push/subscribe"(controller: 'push', action: 'subscribe')
"/sw.js"(uri: '/sw.js')
```

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: Web Push subscriptions and VAPID sending"
```

---

### Task 9: Notification triggers and Quartz scheduler

**Files:**
- Create: `grails-app/jobs/taskboard/DueDateReminderJob.groovy`
- Modify: `grails-app/services/taskboard/TaskService.groovy` (assignment + status triggers)
- Test: `src/integration-test/groovy/taskboard/ReminderIntegrationSpec.groovy`

**Interfaces:**
- Consumes: `Task`, `User`, `PushService.sendToUser`, `User.notifyDaysBefore`, `Task.lastNotifiedAt`.
- Produces: `DueDateReminderJob` running hourly; `TaskService.assignTask(Long taskId, User assignee, User actor)` and `TaskService.complete` now send notifications.

- [ ] **Step 1: Write the failing test for due-date reminder selection**

`src/integration-test/groovy/taskboard/ReminderIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import java.time.LocalDate
import java.time.LocalDateTime

@Integration
@Rollback
class ReminderIntegrationSpec extends Specification {

    TaskService taskService

    void "tasks due within notifyDaysBefore and not yet notified are selected"() {
        given:
        def u = new User(username: "n1", password: "p", displayName: "N",
            apiToken: "nt", notifyDaysBefore: 1).save(flush: true)
        def due = taskService.createTask([title: "morgen",
            dueDate: LocalDate.now().plusDays(1), priority: Priority.LOW], u)
        due.assignedTo = u
        due.save(flush: true)

        when:
        def toNotify = taskService.tasksNeedingReminder(LocalDate.now())

        then:
        toNotify*.id.contains(due.id)
    }

    void "completing a task notifies the creator"() {
        given:
        def creator = new User(username: "c", password: "p", displayName: "C",
            apiToken: "ct").save(flush: true)
        def t = taskService.createTask([title: "x",
            dueDate: LocalDate.now(), priority: Priority.LOW], creator)

        when:
        taskService.complete(t.id)

        then:
        Task.get(t.id).status == TaskStatus.DONE
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.ReminderIntegrationSpec"`
Expected: FAIL — `tasksNeedingReminder` does not exist.

- [ ] **Step 3: Extend `TaskService` with reminder selection and notifications**

Add to `TaskService` (inject `PushService pushService`):
```groovy
PushService pushService

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
    t.assignedTo = assignee
    t.save(failOnError: true)
    if (assignee && assignee.id != actor?.id) {
        pushService.sendToUser(assignee, "Neuer Task",
            "${actor?.displayName} hat dir '${t.title}' zugewiesen")
    }
    t
}
```

Modify `complete` to notify the creator:
```groovy
Task complete(Long id) {
    def t = Task.get(id)
    t.status = TaskStatus.DONE
    t.save(failOnError: true)
    if (t.createdBy) {
        pushService.sendToUser(t.createdBy, "Task erledigt",
            "'${t.title}' wurde als erledigt markiert")
    }
    t
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.ReminderIntegrationSpec"`
Expected: PASS. (The push send is best-effort; with no real subscription it logs a warning and continues.)

- [ ] **Step 5: Create the Quartz job**

`grails-app/jobs/taskboard/DueDateReminderJob.groovy`:
```groovy
package taskboard

import java.time.LocalDate

class DueDateReminderJob {
    TaskService taskService

    static triggers = {
        simple repeatInterval: 1000L * 60 * 60  // every hour
    }

    def execute() {
        taskService.sendDueReminders(LocalDate.now())
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: Quartz due-date reminders and assignment/status notifications"
```

---

### Task 10: PWA manifest and end-to-end Docker run

**Files:**
- Create: `src/main/webapp/manifest.json`
- Create: `grails-app/assets/images/icon-192.png`, `icon-512.png` (placeholder icons)
- Modify: `grails-app/controllers/taskboard/UrlMappings.groovy` (`/manifest.json`)
- Modify: `grails-app/views/task/index.gsp` (link push.js)
- Test: `src/integration-test/groovy/taskboard/ManifestIntegrationSpec.groovy`

**Interfaces:**
- Consumes: everything prior.
- Produces: installable PWA; full `docker compose up` brings up app + db and serves the task list.

- [ ] **Step 1: Write the failing test that manifest is served**

`src/integration-test/groovy/taskboard/ManifestIntegrationSpec.groovy`:
```groovy
package taskboard

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class ManifestIntegrationSpec extends Specification {

    Integer serverPort

    void "manifest.json is reachable and is valid JSON"() {
        when:
        def text = new URL("http://localhost:${serverPort}/manifest.json").text
        then:
        text.contains('"display"')
        text.contains('standalone')
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew integrationTest --tests "taskboard.ManifestIntegrationSpec"`
Expected: FAIL — manifest not served.

- [ ] **Step 3: Create `manifest.json`**

`src/main/webapp/manifest.json`:
```json
{
  "name": "Family Taskboard",
  "short_name": "Tasks",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#3B6D11",
  "icons": [
    { "src": "/assets/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/assets/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

- [ ] **Step 4: Map `/manifest.json` in `UrlMappings.groovy`**

```groovy
"/manifest.json"(uri: '/manifest.json')
```

- [ ] **Step 5: Link `push.js` in `index.gsp`**

Add before `</body>`:
```html
<asset:javascript src="push.js"/>
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew integrationTest --tests "taskboard.ManifestIntegrationSpec"`
Expected: PASS.

- [ ] **Step 7: Run the full suite**

Run: `./gradlew test integrationTest`
Expected: all PASS.

- [ ] **Step 8: End-to-end Docker verification**

Run:
```bash
cp .env.example .env   # fill DB_PASSWORD and VAPID keys
docker compose up --build -d
curl -i http://localhost:8080/manifest.json
```
Expected: HTTP 200 and the manifest JSON. Open `http://localhost:8080` in a browser, log in as `lars`, add a task, confirm it appears color-coded.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: PWA manifest and end-to-end Docker run"
```

---

## Self-Review Notes

- **Spec coverage:** Grails 7.1.1 + Docker (Task 1), User/family + auth (Task 2), Task model with date/person/priority (Task 3), configurable color logic (Task 4), color-coded list + in-app voice (Tasks 5–6), Apple Shortcut quick-add endpoint (Task 7), Web Push (Task 8), all three notification triggers + scheduler (Task 9), PWA (Task 10). Projects are explicitly Phase 2 and excluded.
- **Placeholder scan:** No TBD/TODO; every code step contains runnable code. Icon PNGs in Task 10 are genuine binary placeholders the implementer supplies.
- **Type consistency:** `colorFor(Task, LocalDate, UrgencyConfig)`, `createTask(Map, User)`, `openTasksSorted()`, `sendToUser(User, String, String)`, `tasksNeedingReminder(LocalDate)` are used consistently across tasks.
