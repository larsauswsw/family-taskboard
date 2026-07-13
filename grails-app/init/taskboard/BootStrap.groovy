package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BootStrap {
    def init = { servletContext ->
        seedUsers()
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

    @Transactional
    void seedUrgencyConfig() {
        if (!UrgencyConfig.count()) {
            new UrgencyConfig().save(flush: true)
        }
    }

    def destroy = {}
}
