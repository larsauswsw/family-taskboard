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
