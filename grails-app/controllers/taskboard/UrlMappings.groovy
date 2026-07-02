package taskboard

class UrlMappings {

    static mappings = {
        // Must come before the generic catch-all below, which would otherwise
        // wrongly parse this as controller='api', action='tasks', id='quick'.
        post "/api/tasks/quick"(controller: 'apiTask', action: 'quick')

        "/$namespace/$controller/$action?/$id?(.$format)?" {}
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller: 'task', action: 'index')
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
