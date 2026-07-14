package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Integration
@Rollback
class PushServiceIntegrationSpec extends Specification {

    PushService pushService

    void "publicKey is exposed from configuration"() {
        expect:
        pushService.publicKey() != null
    }

    void "subscriptions are stored per user"() {
        given:
        def u = new User(username: "pu", password: "p",
            displayName: "P", apiToken: "pt").save(flush: true)
        when:
        new PushSubscription(endpoint: "https://ep/1", p256dh: "key",
            auth: "auth", user: u).save(flush: true)
        then:
        PushSubscription.findAllByUser(u).size() == 1
    }

    void "saveSubscription persists a new subscription for the user"() {
        given:
        def u = new User(username: "save-u1", password: "p",
            displayName: "P", apiToken: "save-t1").save(flush: true)

        when:
        pushService.saveSubscription(u, "https://ep/save1", "key", "auth")

        then:
        PushSubscription.findAllByUser(u)*.endpoint == ["https://ep/save1"]
    }

    void "saveSubscription ignores an endpoint that's already registered"() {
        given:
        def u = new User(username: "save-u2", password: "p",
            displayName: "P", apiToken: "save-t2").save(flush: true)
        pushService.saveSubscription(u, "https://ep/save2", "key", "auth")

        when: "the same device re-subscribes with the same endpoint"
        pushService.saveSubscription(u, "https://ep/save2", "key2", "auth2")

        then: "no duplicate row and no unique-constraint failure"
        PushSubscription.findAllByUser(u).size() == 1
    }
}
