package taskboard

import org.springframework.security.core.context.SecurityContextHolder

/**
 * Admin-only CRUD for family member accounts. No @Secured annotation here --
 * SecurityConfig's requestMatchers('/userManagement/**').hasRole('ADMIN')
 * already blocks non-admins with a 403 (server-side forward to /login/denied)
 * before any action runs, same enforcement style as TaskController/
 * SettingsController relying on SecurityConfig for authentication.
 */
class UserManagementController {

    static defaultAction = 'list'

    UserService userService

    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def list() {
        render view: 'list', model: [users: User.list(sort: 'username'), error: null,
            currentUserId: currentUser().id]
    }

    def create() {
        def result = userService.createUser(params.username, params.password, params.displayName,
            params.email, params.admin == 'true')
        render template: 'manage', model: [users: User.list(sort: 'username'), currentUserId: currentUser().id,
            error: result ? null : 'Ungültige Angaben oder Benutzername bereits vergeben (Passwort mind. 8 Zeichen).']
    }

    def update(Long id) {
        def result = userService.updateUser(id, params.displayName, params.email,
            params.admin == 'true', params.newPassword)
        render template: 'manage', model: [users: User.list(sort: 'username'), currentUserId: currentUser().id,
            error: result ? null : 'Ungültige Angaben (neues Passwort mind. 8 Zeichen, falls gesetzt).']
    }

    def delete(Long id) {
        String error = userService.deleteUser(id, currentUser())
        render template: 'manage', model: [users: User.list(sort: 'username'), currentUserId: currentUser().id,
            error: error]
    }
}
