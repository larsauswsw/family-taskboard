package taskboard

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers('/assets/**', '/manifest.json', '/sw.js', '/error',
                                     '/login/**', '/logout/**', '/api/**').permitAll()
                auth.anyRequest().authenticated()
            }
            .formLogin { form ->
                form.loginPage('/login/auth').defaultSuccessUrl('/', true).permitAll()
            }
            .logout { logout ->
                logout.logoutUrl('/logout').logoutSuccessUrl('/login/auth')
            }
            // CSRF on for session forms; off for /api/** which uses Bearer token auth
            .csrf { csrf ->
                csrf.csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers('/api/**')
            }
        http.build()
    }
}
