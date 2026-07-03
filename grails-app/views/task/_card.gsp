<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <g:if test="${task.project}">
        <span class="project-chip" style="background-color: ${task.project.color};">${task.project.name}</span>
    </g:if>
    <div class="task-meta">
        <select name="assignedTo" class="assignee-select"
                hx-post="${createLink(action: 'assign', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.assignedTo ? '' : 'selected'}>—</option>
            <g:each in="${users}" var="u">
                <option value="${u.id}" ${task.assignedTo?.id == u.id ? 'selected' : ''}>${u.displayName}</option>
            </g:each>
        </select>
        <select name="project" class="project-select"
                hx-post="${createLink(action: 'assignProject', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.project ? '' : 'selected'}>Kein Projekt</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}" ${task.project?.id == p.id ? 'selected' : ''}>${p.name}</option>
            </g:each>
        </select>
        <span><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority}</span>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
</div>
