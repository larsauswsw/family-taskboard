package taskboard

class UrlMappings {

    static mappings = {
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
