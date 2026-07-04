package taskboard

import grails.gorm.transactions.Transactional
import java.security.SecureRandom

/** Own-account operations on User -- currently just API token regeneration
 *  (see docs/superpowers/specs/2026-07-04-family-taskboard-settings-design.md). */
@Transactional
class UserService {

    private static final String TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    private static final int TOKEN_LENGTH = 8
    private static final SecureRandom RANDOM = new SecureRandom()

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
}
