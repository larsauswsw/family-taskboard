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
                auth.requestMatchers('/assets/**', '/manifest.json', '/sw.js',
                                     '/login/**', '/logout/**', '/api/**').permitAll()
                auth.anyRequest().authenticated()
            }
            .formLogin { form ->
                form.loginPage('/login/auth').defaultSuccessUrl('/', true).permitAll()
            }
            .logout { logout ->
                logout.logoutUrl('/logout').logoutSuccessUrl('/login/auth')
            }
            .csrf { csrf -> csrf.disable() }
        http.build()
    }
}
