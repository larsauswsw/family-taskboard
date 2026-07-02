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
