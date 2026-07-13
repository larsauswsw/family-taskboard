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
     *  Returns null on success, or a specific German validation-failure
     *  message (duplicate username, blank field, too-short password, ...)
     *  otherwise -- same convention as changeOwnPassword, so the controller
     *  can show the actual reason instead of a generic catch-all. */
    String createUser(String username, String password, String displayName, String email, boolean admin) {
        if (!password || password.length() < 8) return 'Passwort muss mindestens 8 Zeichen lang sein.'
        def u = new User(username: username, password: passwordEncoder.encode(password),
            displayName: displayName, email: email ?: null, admin: admin,
            apiToken: UUID.randomUUID().toString())
        u.save() ? null : describeValidationError(u.errors)
    }

    /** Updates profile fields and admin flag; only resets the password when
     *  newPassword is non-blank, so leaving it empty in the edit form keeps
     *  the existing password. Returns null on success, or a specific German
     *  validation-failure message otherwise (same convention as createUser). */
    String updateUser(Long id, String displayName, String email, boolean admin, String newPassword) {
        def u = User.get(id)
        if (!u) return 'Nutzer wurde nicht gefunden.'
        if (newPassword && newPassword.length() < 8) {
            return 'Neues Passwort muss mindestens 8 Zeichen lang sein.'
        }
        u.displayName = displayName
        u.email = email ?: null
        u.admin = admin
        if (newPassword) {
            u.password = passwordEncoder.encode(newPassword)
        }
        u.save() ? null : describeValidationError(u.errors)
    }

    /** Maps the first GORM field-validation failure to a specific German
     *  message. Field/code pairs verified against User's constraints
     *  (username unique+blank, displayName blank, email format). */
    private static String describeValidationError(errors) {
        def fieldError = errors.fieldErrors[0]
        switch (fieldError?.field) {
            case 'username':
                return fieldError.code == 'unique' ? 'Benutzername bereits vergeben.' : 'Benutzername darf nicht leer sein.'
            case 'displayName':
                return 'Anzeigename darf nicht leer sein.'
            case 'email':
                return 'E-Mail-Adresse ist ungültig.'
            default:
                return 'Ungültige Angaben.'
        }
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
