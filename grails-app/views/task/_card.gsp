<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <div class="task-meta">
        <span>${task.assignedTo?.displayName ?: '—'}</span>
        <span><g:formatDate date="${java.sql.Date.valueOf(task.dueDate)}" format="dd.MM."/></span>
        <span class="badge">${task.priority}</span>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
</div>
