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
