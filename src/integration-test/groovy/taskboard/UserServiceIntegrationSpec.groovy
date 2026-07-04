package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Integration
@Rollback
class UserServiceIntegrationSpec extends Specification {

    UserService userService

    void "regenerateApiToken replaces the token with a new 8-character alphanumeric value"() {
        given:
        def u = new User(username: "settings-u1", password: "p",
            displayName: "U", apiToken: "old-token-uuid-format").save(flush: true)

        when:
        def result = userService.regenerateApiToken(u)

        then:
        result.apiToken != "old-token-uuid-format"
        result.apiToken.length() == 8
        result.apiToken ==~ /[A-Za-z0-9]{8}/
        User.get(u.id).apiToken == result.apiToken
    }

    void "regenerateApiToken generates a different token on each call"() {
        given:
        def u = new User(username: "settings-u2", password: "p",
            displayName: "U", apiToken: "seed").save(flush: true)

        when:
        String first = userService.regenerateApiToken(u).apiToken
        String second = userService.regenerateApiToken(u).apiToken

        then:
        first != second
    }
}
