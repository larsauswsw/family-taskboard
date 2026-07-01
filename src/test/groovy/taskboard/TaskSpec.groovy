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
