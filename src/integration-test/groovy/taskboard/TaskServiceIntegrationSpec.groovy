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
