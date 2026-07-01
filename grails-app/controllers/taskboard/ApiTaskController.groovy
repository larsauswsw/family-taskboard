package taskboard

import grails.converters.JSON
import java.time.LocalDate

class ApiTaskController {

    TaskService taskService

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
