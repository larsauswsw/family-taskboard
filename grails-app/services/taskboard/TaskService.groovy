package taskboard

import grails.gorm.transactions.Transactional
import java.time.LocalDate

@Transactional
class TaskService {

    Task createTask(Map params, User creator) {
        def task = new Task(
            title: params.title,
            dueDate: params.dueDate ?: LocalDate.now(),
            priority: params.priority ?: Priority.MEDIUM,
            description: params.description,
            assignedTo: params.assignedTo,
            createdBy: creator
        )
        task.save(failOnError: true)
        task
    }

    List<Task> openTasksSorted() {
        Task.findAllByStatusNotEqual(TaskStatus.DONE, [sort: 'dueDate', order: 'asc'])
    }

    Task complete(Long id) {
        def t = Task.get(id)
        t.status = TaskStatus.DONE
        t.save(failOnError: true)
        t
    }
}
