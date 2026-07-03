<div id="project-filter">
    <a href="#" class="project-pill ${selectedProject == null ? 'active' : ''}"
       hx-get="${createLink(action: 'list')}" hx-target="#task-list" hx-swap="innerHTML">Alle</a>
    <a href="#" class="project-pill ${selectedProject == 'none' ? 'active' : ''}"
       hx-get="${createLink(action: 'list', params: [project: 'none'])}"
       hx-target="#task-list" hx-swap="innerHTML">Kein Projekt</a>
    <g:each in="${projects}" var="p">
        <a href="#" class="project-pill ${selectedProject == p.id.toString() ? 'active' : ''}"
           style="background-color: ${p.color};"
           hx-get="${createLink(action: 'list', params: [project: p.id])}"
           hx-target="#task-list" hx-swap="innerHTML">${p.name}</a>
    </g:each>
</div>
<g:each in="${tasks}" var="task">
    <g:render template="card"
        model="[task: task, color: urgencyService.colorFor(task, today), users: users, projects: projects]"/>
</g:each>
