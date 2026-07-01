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
