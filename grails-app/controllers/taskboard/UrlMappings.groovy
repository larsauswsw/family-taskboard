package taskboard

class UrlMappings {

    static mappings = {
        // Must come before the generic catch-all below, which would otherwise
        // wrongly parse this as controller='api', action='tasks', id='quick'.
        post "/api/tasks/quick"(controller: 'apiTask', action: 'quick')

        // Web Push routes -- also must precede the catch-all for the same reason
        // (it would otherwise parse "/push/key" as controller='push', action='key'
        // anyway by coincidence, but "/sw.js" needs the explicit uri-forward mapping
        // since it isn't a controller action at all -- it's a static file in
        // src/main/webapp/sw.js).
        "/push/key"(controller: 'push', action: 'key')
        post "/push/subscribe"(controller: 'push', action: 'subscribe')
        "/sw.js"(uri: '/sw.js')

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
