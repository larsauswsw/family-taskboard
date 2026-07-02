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
}
