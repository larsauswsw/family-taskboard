import taskboard.UserDetailsServiceImpl
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

beans = {
    userDetailsService(UserDetailsServiceImpl)
    passwordEncoder(BCryptPasswordEncoder)
}
