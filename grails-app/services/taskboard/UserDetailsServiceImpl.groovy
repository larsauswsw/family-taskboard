package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Registered as the "userDetailsService" bean in resources.groovy and wired
 * into SecurityConfig's explicit AuthenticationProvider bean. Returns User
 * directly (it implements UserDetails), so the authenticated principal
 * elsewhere in the app IS a User instance -- see TaskController.currentUser().
 */
@Transactional(readOnly = true)
class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    User loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = User.findByUsername(username)
        if (!user) throw new UsernameNotFoundException("User '${username}' not found")
        user
    }
}
