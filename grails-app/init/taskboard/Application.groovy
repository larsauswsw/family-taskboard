package taskboard

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

import groovy.transform.CompileStatic

@CompileStatic
// Required: Grails component-scans grails-app/** artefacts, not arbitrary
// @Configuration classes under src/main/groovy. Without this @Import,
// SecurityConfig's SecurityFilterChain bean is silently never created, and
// Spring Boot Actuator's default security auto-configuration takes over
// instead, locking every request (even ones meant to be public) behind a
// generated HTTP Basic password.
@Import(SecurityConfig)
// Enables Spring's @Scheduled annotation processing (used by
// DueDateReminderJob, a grails-app/services artefact -- see its class
// comment for why this project uses Spring scheduling instead of the Grails
// Quartz plugin, which is incompatible with Grails 7.1.1/Groovy 4).
@EnableScheduling
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}