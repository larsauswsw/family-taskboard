package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification

/**
 * Covers the admin-flag migration path added alongside user management:
 * ensureAtLeastOneAdmin() must promote an existing (pre-admin-flag) account
 * on the next boot, since seedUsers() only ever runs on a brand-new install.
 */
@Integration
@Rollback
class BootStrapSpec extends Specification {

    void "ensureAtLeastOneAdmin promotes the oldest user when no admin exists yet"() {
        given: "no admin exists -- demote whatever BootStrap's own seeding already granted admin to, simulating a database that already had users before the admin flag existed"
        User.findAllByAdmin(true).each { it.admin = false; it.save(flush: true) }
        new User(username: "pre-existing", password: "p", displayName: "Pre-Existing",
            apiToken: "pre-t").save(flush: true)

        when:
        new BootStrap().ensureAtLeastOneAdmin()

        then:
        User.countByAdmin(true) == 1
    }

    void "ensureAtLeastOneAdmin is a no-op once an admin already exists"() {
        given:
        def admin = new User(username: "already-admin", password: "p", displayName: "Admin",
            apiToken: "admin-t", admin: true).save(flush: true)
        def member = new User(username: "regular-member", password: "p", displayName: "Member",
            apiToken: "member-t").save(flush: true)

        when:
        new BootStrap().ensureAtLeastOneAdmin()

        then: "the non-admin member is not touched"
        User.get(member.id).admin == false
        User.get(admin.id).admin == true
    }

    void "ensureAtLeastOneAdmin does nothing when there are no users at all"() {
        given:
        User.list().each { it.delete(flush: true) }

        expect:
        User.count() == 0

        when:
        new BootStrap().ensureAtLeastOneAdmin()

        then:
        noExceptionThrown()
        User.count() == 0
    }
}
