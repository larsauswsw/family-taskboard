<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <div class="task-meta">
        <select name="assignedTo" class="assignee-select"
                hx-post="${createLink(action: 'assign', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.assignedTo ? '' : 'selected'}>—</option>
            <g:each in="${users}" var="u">
                <option value="${u.id}" ${task.assignedTo?.id == u.id ? 'selected' : ''}>${u.displayName}</option>
            </g:each>
        </select>
        <span><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority}</span>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
</div>
