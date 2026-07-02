package taskboard

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * A family member. Implements Spring Security's UserDetails directly (this
 * project has no spring-security-core plugin / Role-Role domains) and is
 * always ROLE_USER -- there is no admin/member distinction.
 */
class User implements UserDetails {
    String username
    String password
    String displayName
    String email
    /** Bearer token for the stateless REST quick-add endpoint (Apple Shortcuts). */
    String apiToken
    Integer notifyDaysBefore = 1
    boolean notifyOnDueDate = true

    static mapping = {
        table 'app_user' // "user" is a reserved word in H2 (used by the test profile)
    }

    static constraints = {
        password nullable: false, blank: false
        username nullable: false, blank: false, unique: true
        displayName nullable: false, blank: false
        email nullable: true, email: true
        apiToken nullable: true, unique: true
    }

    @Override Collection<? extends GrantedAuthority> getAuthorities() {
        [new SimpleGrantedAuthority('ROLE_USER')]
    }
    @Override boolean isAccountNonExpired() { true }
    @Override boolean isAccountNonLocked() { true }
    @Override boolean isCredentialsNonExpired() { true }
    @Override boolean isEnabled() { true }
}
