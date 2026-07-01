package taskboard

import grails.gorm.transactions.Transactional
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

@Transactional(readOnly = true)
class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    User loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = User.findByUsername(username)
        if (!user) throw new UsernameNotFoundException("User '${username}' not found")
        user
    }
}
