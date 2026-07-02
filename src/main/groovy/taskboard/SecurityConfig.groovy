package taskboard

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
@EnableWebSecurity
class SecurityConfig {

    // Spring Security 6 defers actually generating/saving the CSRF token until
    // something reads request attribute "_csrf" (e.g. a view rendering a hidden
    // form field). Without this, CookieCsrfTokenRepository never writes the
    // XSRF-TOKEN cookie on pages that don't happen to reference _csrf (e.g. the
    // task list), so HTMX's cookie-read/header-echo shim would find nothing to
    // send on the very first quick-add after login. Forcing resolution here
    // guarantees every response carries a fresh, valid cookie.
    private static class CsrfCookieFilter extends OncePerRequestFilter {
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.name)
            csrfToken?.getToken()
            chain.doFilter(request, response)
        }
    }

    // Wired explicitly rather than relying on Spring Boot's UserDetailsService
    // auto-detection: Grails registers our "userDetailsService" bean (from
    // resources.groovy) late enough that Boot's @ConditionalOnMissingBean check
    // doesn't see it, so Boot ALSO creates its own inMemoryUserDetailsManager.
    // With two UserDetailsService beans present, Spring Security's automatic
    // AuthenticationManager wiring can't pick one and login silently always
    // fails (never even reaching our UserDetailsServiceImpl). Defining this
    // provider explicitly sidesteps that ambiguity entirely.
    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsServiceImpl userDetailsService,
                                                   PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider()
        provider.setUserDetailsService(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        provider
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers('/assets/**', '/manifest.json', '/sw.js', '/error',
                                     '/login/**', '/logout/**', '/api/**').permitAll()
                auth.anyRequest().authenticated()
            }
            .formLogin { form ->
                form.loginPage('/login/auth').loginProcessingUrl('/login')
                    .defaultSuccessUrl('/', true).permitAll()
            }
            .logout { logout ->
                logout.logoutUrl('/logout').logoutSuccessUrl('/login/auth')
            }
            // CSRF on for session forms; off for /api/** which uses Bearer token auth.
            // Uses the plain (non-Xor) request handler: Spring Security 6's default
            // XorCsrfTokenRequestAttributeHandler masks the token per-request for BREACH
            // protection, which breaks the cookie-read/header-echo pattern our HTMX pages
            // rely on (JS reads the raw XSRF-TOKEN cookie and sends it back verbatim as
            // X-XSRF-TOKEN -- the masked default would reject that value). This is Spring's
            // own documented trade-off for cookie-based CSRF with SPA/AJAX-style clients.
            .csrf { csrf ->
                csrf.csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers('/api/**')
            }
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter)
        http.build()
    }
}
