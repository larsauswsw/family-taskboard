package taskboard

/**
 * Project management: create/edit/delete, reachable from its own page
 * (grails-app/views/project/list.gsp, linked from the navbar's folder icon)
 * as of the design-system rollout -- previously an inline <details> widget
 * on the task list page. No @Secured annotation -- relies on SecurityConfig's
 * anyRequest().authenticated(), same as TaskController; /project/** is not
 * in the permitAll matcher list.
 */
class ProjectController {

    static defaultAction = 'list'

    ProjectService projectService

    /** Full page (GET /project) -- unlike create/update/delete below, this is
     *  a real page load, not an HTMX fragment swap, so it renders the 'list'
     *  view (which wraps the 'manage' fragment in a full HTML document with
     *  the shared navbar) rather than the bare template. */
    def list() {
        render view: 'list', model: [projects: Project.list(), error: null]
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
