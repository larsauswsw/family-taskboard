package taskboard

import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDate

/**
 * Hourly due-date reminder sweep, implemented as a plain Grails service rather
 * than a grails-app/jobs Quartz job: this project has no Quartz plugin
 * (org.grails.plugins:quartz 3.x is incompatible with Grails 7.1.1/Groovy 4 --
 * decided in Task 1, see .superpowers/sdd/progress.md). Spring's @Scheduled
 * annotation processing is enabled via @EnableScheduling on Application.groovy.
 *
 * This class lives under grails-app/services (not src/main/groovy) specifically
 * so Grails auto-registers it as a Spring bean the same way it registers
 * TaskService and PushService -- classes under src/main/groovy are NOT
 * component-scanned by Grails, which caused a serious multi-bug incident with
 * SecurityConfig in Task 7. Putting this class here sidesteps that gap entirely.
 *
 * IMPORTANT: the class name must end in "Service" -- Grails' ServiceArtefactHandler
 * only registers classes under grails-app/services as Spring beans if their simple
 * name matches that suffix (confirmed by bytecode inspection of grails-core, and
 * empirically: with the name "DueDateReminderJob" this bean was silently never
 * created and the @Scheduled method never fired, found by review before this was
 * ever deployed). "grails-app/services" is not a magic "everything here is a bean"
 * directory; the naming convention is what makes it one.
 *
 * IMPORTANT #2: `static lazyInit = false` is required too. Grails services are
 * lazily initialized by default; ScheduledAnnotationBeanPostProcessor only picks
 * up @Scheduled methods on a bean at the moment that bean is actually instantiated.
 * Since nothing else in the app ever injects THIS service (it's the trigger, not a
 * dependency of anything), it would otherwise never be instantiated and its
 * @Scheduled method would never run -- confirmed empirically by starting the app
 * with a 2s interval and observing zero executions until this flag was added.
 */
class DueDateReminderJobService {

    static lazyInit = false

    TaskService taskService

    @Scheduled(fixedRate = 3600000L) // every hour
    void execute() {
        taskService.sendDueReminders(LocalDate.now())
    }
}
