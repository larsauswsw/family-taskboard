package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BootStrap {
    def init = { servletContext ->
        seedUsers()
    }

    @Transactional
    void seedUsers() {
        if (!User.count()) {
            def encoder = new BCryptPasswordEncoder()
            new User(username: 'lars', password: encoder.encode('changeme'),
                displayName: 'Lars', apiToken: UUID.randomUUID().toString()).save(flush: true)
        }
    }

    def destroy = {}
}
