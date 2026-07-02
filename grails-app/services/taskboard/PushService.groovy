package taskboard

import grails.gorm.transactions.Transactional
import nl.martijndwars.webpush.PushService as WebPush
import nl.martijndwars.webpush.Notification
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Sends Web Push notifications via the web-push library (nl.martijndwars:web-push:5.1.1)
 * using VAPID keys configured in application.yml (vapid.publicKey/privateKey/subject).
 *
 * NOTE: this project has no spring-security-core plugin, so there's no
 * springSecurityService here either -- callers pass in a resolved User directly.
 */
@Transactional
class PushService {

    def grailsApplication

    String publicKey() {
        grailsApplication.config.getProperty('vapid.publicKey')
    }

    /**
     * Best-effort: callers (TaskService's completion/assignment/reminder notifications)
     * are never supposed to fail because push delivery failed, including when no VAPID
     * keypair is configured at all (the default in dev/test -- see application.yml). The
     * web-push library's setPublicKey/setPrivateKey throw (ArrayIndexOutOfBoundsException,
     * not a friendlier exception) on empty key material, so that setup is guarded by the
     * same try/catch as the actual send, not just the per-subscription loop.
     */
    void sendToUser(User user, String title, String body) {
        try {
            Security.addProvider(new BouncyCastleProvider())
            def cfg = grailsApplication.config
            def web = new WebPush()
            web.setPublicKey(cfg.getProperty('vapid.publicKey'))
            web.setPrivateKey(cfg.getProperty('vapid.privateKey'))
            web.setSubject(cfg.getProperty('vapid.subject'))

            String payload = "{\"title\":\"${title}\",\"body\":\"${body}\"}"
            PushSubscription.findAllByUser(user).each { sub ->
                try {
                    web.send(new Notification(sub.endpoint, sub.p256dh, sub.auth,
                        payload.getBytes('UTF-8')))
                } catch (Exception e) {
                    log.warn("Push failed for ${sub.endpoint}: ${e.message}")
                }
            }
        } catch (Exception e) {
            log.warn("Push setup failed (VAPID keys not configured?): ${e.message}")
        }
    }
}
