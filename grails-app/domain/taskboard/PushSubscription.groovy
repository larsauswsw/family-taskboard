package taskboard

/**
 * A browser's Web Push subscription (from the PushManager API), tied to the
 * User who registered it. endpoint is the push service URL; p256dh/auth are
 * the base64url-encoded keys used to encrypt notification payloads.
 */
class PushSubscription {
    String endpoint
    String p256dh
    String auth
    User user

    static constraints = {
        endpoint nullable: false, unique: true, maxSize: 1000
        p256dh nullable: false
        auth nullable: false
    }
}
