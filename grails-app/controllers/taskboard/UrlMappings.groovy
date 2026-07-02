package taskboard

class UrlMappings {

    static mappings = {
        // Must come before the generic catch-all below, which would otherwise
        // wrongly parse this as controller='api', action='tasks', id='quick'.
        post "/api/tasks/quick"(controller: 'apiTask', action: 'quick')

        // Web Push routes -- also must precede the catch-all for the same reason
        // (it would otherwise parse "/push/key" as controller='push', action='key'
        // anyway by coincidence). /sw.js and /manifest.json are static files
        // under src/main/resources/static/, served by Spring Boot's default
        // static-resource handler -- but Grails' generic catch-all mapping below
        // would otherwise parse "/manifest.json" as controller='manifest',
        // format='json' and short-circuit with its own 404 before Spring's
        // resource handler gets a chance. Forwarding to a *different* uri
        // (the classpath-relative /static/... path) avoids both problems: it
        // takes precedence over the catch-all, and (unlike forwarding a path to
        // itself, which recurses infinitely -- see git history) it lands on a
        // path no UrlMappings entry claims, so it falls through to Spring's
        // static handler.
        "/sw.js"(uri: '/static/sw.js')
        "/manifest.json"(uri: '/static/manifest.json')
        "/push/key"(controller: 'push', action: 'key')
        post "/push/subscribe"(controller: 'push', action: 'subscribe')

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
