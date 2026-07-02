import taskboard.UserDetailsServiceImpl
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

// Consumed by SecurityConfig's explicit AuthenticationProvider bean, not by
// Spring Boot's UserDetailsService auto-detection -- see the comment on
// SecurityConfig.authenticationProvider() for why that auto-detection doesn't
// reliably see beans registered here.
beans = {
    userDetailsService(UserDetailsServiceImpl)
    passwordEncoder(BCryptPasswordEncoder)
}
