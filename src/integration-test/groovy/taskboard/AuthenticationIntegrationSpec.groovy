package taskboard

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder

@Integration
@Rollback
class AuthenticationIntegrationSpec extends Specification {

    UserDetailsService userDetailsService
    PasswordEncoder passwordEncoder

    void "userDetailsService loads a seeded user and bcrypt password matches"() {
        given:
        def raw = "test-secret"
        new User(username: "authtest", password: passwordEncoder.encode(raw),
                 displayName: "Auth Test", apiToken: "auth-tok").save(flush: true)

        when:
        def loaded = userDetailsService.loadUserByUsername("authtest")

        then:
        loaded != null
        loaded.username == "authtest"
        passwordEncoder.matches(raw, loaded.password)
        loaded.authorities.any { it.authority == 'ROLE_USER' }
    }

    void "unknown username throws"() {
        when:
        userDetailsService.loadUserByUsername("does-not-exist")
        then:
        thrown(org.springframework.security.core.userdetails.UsernameNotFoundException)
    }
}
