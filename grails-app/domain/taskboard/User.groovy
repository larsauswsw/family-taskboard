package taskboard

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class User implements UserDetails {
    String username
    String password
    String displayName
    String email
    String apiToken
    Integer notifyDaysBefore = 1
    boolean notifyOnDueDate = true

    static mapping = {
        table 'app_user'
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
