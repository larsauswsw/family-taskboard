package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BootStrap {
    def init = { servletContext ->
        seedUsers()
        ensureAtLeastOneAdmin()
        seedUrgencyConfig()
    }

    // @Transactional is required here: the BootStrap init closure itself does
    // not run inside a transaction, and GORM saves need one.
    @Transactional
    void seedUsers() {
        if (!User.count()) {
            def encoder = new BCryptPasswordEncoder()
            // Change this password before running anywhere beyond local testing.
            new User(username: 'lars', password: encoder.encode('changeme'),
                displayName: 'Lars', apiToken: UUID.randomUUID().toString(), admin: true).save(flush: true)
        }
    }

    /** One-time migration for the `admin` flag introduced alongside user
     *  management: seedUsers() above only sets admin:true on a brand-new
     *  install (User.count() == 0), so a database that already had users
     *  before this flag existed -- e.g. the existing production account --
     *  would otherwise end up with no admin at all after the schema update,
     *  locking every family member out of /userManagement. Runs on every
     *  boot but is a no-op as soon as any admin exists. */
    @Transactional
    void ensureAtLeastOneAdmin() {
        if (User.count() && !User.countByAdmin(true)) {
            User.list(sort: 'id', max: 1)[0]?.with { it.admin = true; it.save(flush: true) }
        }
    }

    @Transactional
    void seedUrgencyConfig() {
        if (!UrgencyConfig.count()) {
            new UrgencyConfig().save(flush: true)
        }
    }

    def destroy = {}
}
