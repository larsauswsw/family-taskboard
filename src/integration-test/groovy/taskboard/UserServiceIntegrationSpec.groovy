package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import org.springframework.security.crypto.password.PasswordEncoder

@Integration
@Rollback
class UserServiceIntegrationSpec extends Specification {

    UserService userService
    PasswordEncoder passwordEncoder

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

    void "changeOwnPassword rejects a wrong current password"() {
        given:
        def u = new User(username: "pw-u1", password: passwordEncoder.encode("correct-horse"),
            displayName: "U", apiToken: "t1").save(flush: true)

        expect:
        userService.changeOwnPassword(u, "wrong", "new-password", "new-password") ==
            'Aktuelles Passwort ist falsch.'
    }

    void "changeOwnPassword rejects a mismatched confirmation"() {
        given:
        def u = new User(username: "pw-u2", password: passwordEncoder.encode("correct-horse"),
            displayName: "U", apiToken: "t2").save(flush: true)

        expect:
        userService.changeOwnPassword(u, "correct-horse", "new-password", "different") ==
            'Die Passwörter stimmen nicht überein.'
    }

    void "changeOwnPassword rejects a too-short new password"() {
        given:
        def u = new User(username: "pw-u3", password: passwordEncoder.encode("correct-horse"),
            displayName: "U", apiToken: "t3").save(flush: true)

        expect:
        userService.changeOwnPassword(u, "correct-horse", "short", "short") ==
            'Neues Passwort muss mindestens 8 Zeichen lang sein.'
    }

    void "changeOwnPassword updates the password when everything checks out"() {
        given:
        def u = new User(username: "pw-u4", password: passwordEncoder.encode("correct-horse"),
            displayName: "U", apiToken: "t4").save(flush: true)

        when:
        String error = userService.changeOwnPassword(u, "correct-horse", "new-password", "new-password")

        then:
        error == null
        passwordEncoder.matches("new-password", User.get(u.id).password)
    }

    void "createUser saves a new family member with an encoded password"() {
        when:
        String error = userService.createUser("newkid", "family-pw", "New Kid", "kid@example.com", false)
        def created = User.findByUsername("newkid")

        then:
        error == null
        created != null
        created.admin == false
        passwordEncoder.matches("family-pw", created.password)
        created.apiToken != null
    }

    void "createUser fails for a duplicate username"() {
        given:
        new User(username: "dup", password: "p", displayName: "D", apiToken: "dup-t").save(flush: true)

        expect:
        userService.createUser("dup", "family-pw", "Dup2", null, false) == 'Benutzername bereits vergeben.'
    }

    void "createUser fails for a too-short password"() {
        expect:
        userService.createUser("shortpw", "1234567", "Short", null, false) ==
            'Passwort muss mindestens 8 Zeichen lang sein.'
    }

    void "createUser fails for a blank display name"() {
        expect:
        userService.createUser("blankname", "family-pw", "", null, false) == 'Anzeigename darf nicht leer sein.'
    }

    void "createUser fails for an invalid email"() {
        expect:
        userService.createUser("bademail", "family-pw", "Bad Email", "not-an-email", false) ==
            'E-Mail-Adresse ist ungültig.'
    }

    void "updateUser changes profile fields and admin flag without touching the password when newPassword is blank"() {
        given:
        String originalPassword = passwordEncoder.encode("keep-me")
        def u = new User(username: "upd-u1", password: originalPassword,
            displayName: "Old Name", apiToken: "upd-t1").save(flush: true)

        when:
        String error = userService.updateUser(u.id, "New Name", "new@example.com", true, "")
        def result = User.get(u.id)

        then:
        error == null
        result.displayName == "New Name"
        result.email == "new@example.com"
        result.admin == true
        result.password == originalPassword
    }

    void "updateUser resets the password when newPassword is provided"() {
        given:
        def u = new User(username: "upd-u2", password: passwordEncoder.encode("old-pw"),
            displayName: "U", apiToken: "upd-t2").save(flush: true)

        when:
        userService.updateUser(u.id, "U", null, false, "brand-new-pw")

        then:
        passwordEncoder.matches("brand-new-pw", User.get(u.id).password)
    }

    void "updateUser fails for a too-short new password without touching the existing one"() {
        given:
        String originalPassword = passwordEncoder.encode("keep-me")
        def u = new User(username: "upd-u3", password: originalPassword,
            displayName: "U", apiToken: "upd-t3").save(flush: true)

        expect:
        userService.updateUser(u.id, "U", null, false, "short") ==
            'Neues Passwort muss mindestens 8 Zeichen lang sein.'
        User.get(u.id).password == originalPassword
    }

    void "deleteUser refuses to delete the acting user's own account"() {
        given:
        def u = new User(username: "self-del", password: "p", displayName: "U", apiToken: "sd-t").save(flush: true)

        expect:
        userService.deleteUser(u.id, u) == 'Der eigene Account kann nicht gelöscht werden.'
        User.get(u.id) != null
    }

    void "deleteUser refuses to delete the last remaining admin"() {
        given: "no other admin exists -- demote the seeded 'lars' account so this admin really is the last one"
        User.findAllByAdmin(true).each { it.admin = false; it.save(flush: true) }
        def admin = new User(username: "sole-admin", password: "p", displayName: "A",
            apiToken: "sa-t", admin: true).save(flush: true)
        def other = new User(username: "other-u", password: "p", displayName: "O", apiToken: "ou-t").save(flush: true)

        expect:
        userService.deleteUser(admin.id, other) == 'Der letzte Administrator kann nicht gelöscht werden.'
        User.get(admin.id) != null
    }

    void "deleteUser succeeds for a non-last-admin, non-self target and clears task references"() {
        given:
        def admin = new User(username: "admin2", password: "p", displayName: "A",
            apiToken: "a2-t", admin: true).save(flush: true)
        def victim = new User(username: "victim", password: "p", displayName: "V", apiToken: "v-t").save(flush: true)
        def task = new Task(title: "t", dueDate: java.time.LocalDate.now(),
            priority: Priority.MEDIUM, assignedTo: victim, createdBy: victim).save(flush: true)

        when:
        String error = userService.deleteUser(victim.id, admin)

        then:
        error == null
        User.get(victim.id) == null
        Task.get(task.id).assignedTo == null
        Task.get(task.id).createdBy == null
    }
}
