package taskboard

import grails.gorm.transactions.Transactional

/** CRUD for Project labels used to group tasks (see Project's docblock for why
 *  there's no GORM belongsTo/hasMany relationship to Task). */
@Transactional
class ProjectService {

    Project create(String name, String color) {
        def p = new Project(name: name, color: color)
        p.save() ? p : null
    }

    Project update(Long id, String name, String color) {
        def p = Project.get(id)
        if (!p) return null
        p.name = name
        p.color = color
        p.save() ? p : null
    }

    /** Nullifies `project` on every referencing task BEFORE deleting the project
     *  itself, so tasks are never destroyed and no dangling foreign key is
     *  possible -- deliberately not relying on GORM's belongsTo/hasMany cascade
     *  for this (see Project's docblock). A no-op for an unknown id. */
    void delete(Long id) {
        def p = Project.get(id)
        if (!p) return
        Task.findAllByProject(p).each { Task t ->
            t.project = null
            t.save(failOnError: true)
        }
        p.delete(flush: true)
    }
}
