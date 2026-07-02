package taskboard

import grails.converters.JSON
import java.time.LocalDate

/**
 * Stateless REST quick-add for Apple Shortcuts (Siri, Watch, Back Tap, Action
 * Button, Lock Screen widget) -- see docs/apple-shortcut.md. Mapped to
 * POST /api/tasks/quick in UrlMappings.groovy, ahead of the generic catch-all
 * route. Auth is a per-user Bearer token (User.apiToken), not a session, so
 * this path is exempt from CSRF and not covered by formLogin (see the
 * /api/** entries in SecurityConfig.groovy).
 */
class ApiTaskController {

    TaskService taskService

    /** 201 + created task JSON, 401 for a missing/unknown token, 422 for empty/missing text. */
    def quick() {
        String auth = request.getHeader('Authorization')
        String token = auth?.startsWith('Bearer ') ? auth.substring(7) : null
        User user = token ? User.findByApiToken(token) : null
        if (!user) {
            render status: 401, contentType: 'application/json',
                text: ([error: 'invalid token'] as JSON).toString()
            return
        }
        def body = request.JSON
        String text = body?.text?.toString()?.trim()
        if (!text) {
            render status: 422, contentType: 'application/json',
                text: ([error: 'empty text'] as JSON).toString()
            return
        }
        def task = taskService.createTask(
            [title: text, dueDate: LocalDate.now(), priority: Priority.MEDIUM], user)
        render status: 201, contentType: 'application/json',
            text: ([id: task.id, title: task.title,
                    dueDate: task.dueDate.toString()] as JSON).toString()
    }
}
