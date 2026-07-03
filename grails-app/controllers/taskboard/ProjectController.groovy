package taskboard

/**
 * Inline project management (create/edit/delete), used from the collapsible
 * "Projekte verwalten" section on the task list page (see task/index.gsp).
 * No @Secured annotation -- relies on SecurityConfig's anyRequest().authenticated(),
 * same as TaskController; /project/** is not in the permitAll matcher list.
 */
class ProjectController {

    ProjectService projectService

    def list() {
        render template: 'manage', model: [projects: Project.list(), error: null]
    }

    def create() {
        def result = projectService.create(params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def update(Long id) {
        def result = projectService.update(id, params.name, params.color)
        render template: 'manage', model: [projects: Project.list(),
            error: result ? null : 'Ungültiger Name oder Farbe.']
    }

    def delete(Long id) {
        projectService.delete(id)
        render template: 'manage', model: [projects: Project.list(), error: null]
    }
}
