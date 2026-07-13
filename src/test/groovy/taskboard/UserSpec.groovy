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

    void "a non-admin user only has ROLE_USER"() {
        expect:
        new User(username: "member", password: "secret", displayName: "Member")
            .authorities*.authority as Set == ["ROLE_USER"] as Set
    }

    void "an admin user additionally has ROLE_ADMIN"() {
        expect:
        new User(username: "admin", password: "secret", displayName: "Admin", admin: true)
            .authorities*.authority as Set == ["ROLE_USER", "ROLE_ADMIN"] as Set
    }
}
