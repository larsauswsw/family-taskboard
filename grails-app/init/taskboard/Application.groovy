package taskboard

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.context.annotation.Import

import groovy.transform.CompileStatic

@CompileStatic
// Required: Grails component-scans grails-app/** artefacts, not arbitrary
// @Configuration classes under src/main/groovy. Without this @Import,
// SecurityConfig's SecurityFilterChain bean is silently never created, and
// Spring Boot Actuator's default security auto-configuration takes over
// instead, locking every request (even ones meant to be public) behind a
// generated HTTP Basic password.
@Import(SecurityConfig)
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}