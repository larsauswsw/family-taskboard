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
 */
class DueDateReminderJob {

    TaskService taskService

    @Scheduled(fixedRate = 3600000L) // every hour
    void execute() {
        taskService.sendDueReminders(LocalDate.now())
    }
}
