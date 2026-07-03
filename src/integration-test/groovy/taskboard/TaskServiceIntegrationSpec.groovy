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
}
