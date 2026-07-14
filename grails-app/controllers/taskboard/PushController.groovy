package taskboard

import org.springframework.security.core.context.SecurityContextHolder
import grails.converters.JSON

/**
 * Web Push subscription endpoints. No @Secured annotation: this project has no
 * spring-security-core plugin, so authentication is enforced entirely by
 * SecurityConfig's anyRequest().authenticated() rule (see SecurityConfig.groovy),
 * which already covers /push/** (it is not in the permitAll list -- only
 * /sw.js itself is, since the service worker script must be fetchable before
 * login).
 */
class PushController {

    PushService pushService

    /** There is no springSecurityService bean here; UserDetailsServiceImpl
     *  returns User instances directly, so the principal already IS a User. */
    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def key() {
        render([publicKey: pushService.publicKey()] as JSON)
    }

    def subscribe() {
        def body = request.JSON
        pushService.saveSubscription(currentUser(), body.endpoint, body.keys.p256dh, body.keys.auth)
        render status: 201, text: '{"ok":true}', contentType: 'application/json'
    }
}
