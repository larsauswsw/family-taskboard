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

    void "complete returns null instead of throwing for an id that no longer exists"() {
        expect: "e.g. a stale HTMX card completed twice from different tabs"
        taskService.complete(-1L) == null
    }

    void "assignTask sets assignedTo and notifies the assignee when assigned by someone else"() {
        given:
        def creator = new User(username: "asg-creator", password: "p",
            displayName: "Creator", apiToken: "asg-c").save(flush: true)
        def assignee = new User(username: "asg-assignee", password: "p",
            displayName: "Assignee", apiToken: "asg-a").save(flush: true)
        def t = taskService.createTask([title: "zu erledigen",
            dueDate: LocalDate.now(), priority: Priority.LOW], creator)

        when:
        def result = taskService.assignTask(t.id, assignee, creator)

        then:
        result.assignedTo.id == assignee.id
        Task.get(t.id).assignedTo.id == assignee.id
    }

    void "assignTask returns null instead of throwing for an id that no longer exists"() {
        given:
        def u = new User(username: "asg-none", password: "p",
            displayName: "U", apiToken: "asg-n").save(flush: true)

        expect:
        taskService.assignTask(-1L, u, u) == null
    }

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

    void "createTask parses a date phrase from the title AND keeps the given project"() {
        given: "the two features (Phase 2 projects, date parsing) must compose correctly"
        def u = new User(username: "date-proj-u", password: "p",
            displayName: "U", apiToken: "dateproju").save(flush: true)
        def project = new Project(name: "Haushalt", color: "#3B82F6").save(flush: true)

        when:
        def t = taskService.createTask([title: "Einkauf morgen",
            priority: Priority.LOW, project: project], u)

        then:
        t.dueDate == LocalDate.now().plusDays(1)
        t.title == "Einkauf"
        t.project?.id == project.id
    }

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

    void "complete on a task with project and assignedTo set retains both on the spawned occurrence"() {
        given:
        def creator = new User(username: "rec-proj-creator", password: "p",
            displayName: "Creator", apiToken: "recprojc").save(flush: true)
        def assignee = new User(username: "rec-proj-assignee", password: "p",
            displayName: "Assignee", apiToken: "recproja").save(flush: true)
        def project = new Project(name: "Haushalt", color: "#3B82F6").save(flush: true)
        def t = taskService.createTask([title: "Bad putzen",
            dueDate: LocalDate.now(), priority: Priority.LOW,
            project: project, assignedTo: assignee], creator)
        taskService.setRecurrence(t.id, RecurrenceType.WEEKLY, 1, null)

        when:
        taskService.complete(t.id)
        def next = Task.findByTitleAndStatus("Bad putzen", TaskStatus.OPEN)

        then:
        next != null
        next.project?.id == project.id
        next.assignedTo?.id == assignee.id
    }

    void "completing an already-DONE recurring task a second time does not spawn a second occurrence"() {
        given: "e.g. a double-click before the HTMX swap removes the button, or two tabs/devices"
        def u = new User(username: "rec-dup-u1", password: "p",
            displayName: "U", apiToken: "recdup1").save(flush: true)
        def t = taskService.createTask([title: "Doppelt-Erledigt-Task",
            dueDate: LocalDate.now(), priority: Priority.LOW], u)
        taskService.setRecurrence(t.id, RecurrenceType.DAILY, 1, null)

        when:
        taskService.complete(t.id)
        taskService.complete(t.id)

        then:
        Task.findAllByTitleAndStatus("Doppelt-Erledigt-Task", TaskStatus.OPEN).size() == 1
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
}
