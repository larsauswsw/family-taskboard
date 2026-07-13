package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.SecureRandom

/** Own-account operations (API token regeneration, password change) plus
 *  admin-only management of other family members' accounts. */
@Transactional
class UserService {

    private static final String TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    private static final int TOKEN_LENGTH = 8
    private static final SecureRandom RANDOM = new SecureRandom()

    PasswordEncoder passwordEncoder

    /** Replaces the user's API token with a fresh 8-character random one,
     *  immediately invalidating the old one -- any Apple Shortcut using the
     *  previous token starts getting 401s until updated with the new value.
     *  Short and alphanumeric by deliberate choice (manual entry is easier
     *  than a 36-char UUID); SecureRandom is used despite the short length
     *  since this is still a bearer credential. No confirmation/undo logic
     *  here -- that's a client-side (browser confirm()) concern only. */
    User regenerateApiToken(User user) {
        user.apiToken = (1..TOKEN_LENGTH).collect { TOKEN_CHARS[RANDOM.nextInt(TOKEN_CHARS.size())] }.join()
        user.save(failOnError: true)
        user
    }

    /** Verifies the current password before setting the new one, and rejects
     *  a mismatched confirmation -- both checks happen here rather than in
     *  the controller so SettingsController stays a thin HTTP shim. Returns
     *  an error message, or null on success. */
    String changeOwnPassword(User user, String currentPassword, String newPassword, String newPasswordConfirm) {
        if (!currentPassword || !passwordEncoder.matches(currentPassword, user.password)) {
            return 'Aktuelles Passwort ist falsch.'
        }
        if (!newPassword || newPassword.length() < 8) {
            return 'Neues Passwort muss mindestens 8 Zeichen lang sein.'
        }
        if (newPassword != newPasswordConfirm) {
            return 'Die Passwörter stimmen nicht überein.'
        }
        user.password = passwordEncoder.encode(newPassword)
        user.save(failOnError: true)
        null
    }

    /** Creates a new family member with an admin-chosen initial password.
     *  Returns the saved User, or null if validation failed (e.g. duplicate
     *  username) -- same null-on-failure convention as ProjectService. */
    User createUser(String username, String password, String displayName, String email, boolean admin) {
        if (!password || password.length() < 8) return null
        def u = new User(username: username, password: passwordEncoder.encode(password),
            displayName: displayName, email: email ?: null, admin: admin,
            apiToken: UUID.randomUUID().toString())
        u.save() ? u : null
    }

    /** Updates profile fields and admin flag; only resets the password when
     *  newPassword is non-blank, so leaving it empty in the edit form keeps
     *  the existing password. Returns the updated User, or null if the user
     *  doesn't exist or validation failed. */
    User updateUser(Long id, String displayName, String email, boolean admin, String newPassword) {
        def u = User.get(id)
        if (!u) return null
        u.displayName = displayName
        u.email = email ?: null
        u.admin = admin
        if (newPassword) {
            if (newPassword.length() < 8) return null
            u.password = passwordEncoder.encode(newPassword)
        }
        u.save() ? u : null
    }

    /** Deletes a family member, refusing to remove the last remaining admin
     *  or the account performing the deletion -- both would either lock the
     *  family out of user management or let someone delete themselves out
     *  from under their own session. Returns an error message, or null on
     *  success. A no-op (no error) for an unknown id. */
    String deleteUser(Long id, User actingUser) {
        def u = User.get(id)
        if (!u) return null
        if (u.id == actingUser.id) {
            return 'Der eigene Account kann nicht gelöscht werden.'
        }
        if (u.admin && User.countByAdmin(true) <= 1) {
            return 'Der letzte Administrator kann nicht gelöscht werden.'
        }
        Task.findAllByAssignedTo(u).each { Task t -> t.assignedTo = null; t.save(failOnError: true) }
        Task.findAllByCreatedBy(u).each { Task t -> t.createdBy = null; t.save(failOnError: true) }
        PushSubscription.findAllByUser(u).each { it.delete() }
        u.delete(flush: true)
        null
    }
}
