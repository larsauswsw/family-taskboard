package taskboard

import org.springframework.security.core.context.SecurityContextHolder

/**
 * Own-account settings: view API token, regenerate it. No @Secured
 * annotation -- covered by SecurityConfig's anyRequest().authenticated(),
 * same as TaskController/ProjectController.
 */
class SettingsController {

    static defaultAction = 'show'

    UserService userService

    /** There is no springSecurityService bean here; UserDetailsServiceImpl
     *  returns User instances directly, so the principal already IS a User. */
    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def show() {
        [user: currentUser()]
    }

    /** Redirect-after-POST avoids re-submitting the token regeneration if the
     *  resulting page is reloaded. */
    def regenerateToken() {
        userService.regenerateApiToken(currentUser())
        redirect action: 'show'
    }
}
