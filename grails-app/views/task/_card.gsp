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
        <g:if test="${task.recurrenceRule?.active}">
            <span class="recurrence-badge" title="Wiederholt sich">🔁</span>
        </g:if>
        <button hx-post="${createLink(action: 'complete', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML">✓</button>
    </div>
    <details class="recurrence-details">
        <summary>🔁 Wiederholung</summary>
        <g:if test="${task.recurrenceRule?.active}">
            <p class="recurrence-summary">Wiederholt sich: ${task.recurrenceRule.type}</p>
            <form hx-post="${createLink(action: 'stopRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <button type="submit">Serie beenden</button>
            </form>
        </g:if>
        <g:else>
            <form class="recurrence-form"
                  hx-post="${createLink(action: 'setRecurrence', id: task.id)}"
                  hx-target="#task-list" hx-swap="innerHTML">
                <select name="type" class="recurrence-type-select"
                        onchange="taskboardToggleRecurrenceFields(this)">
                    <option value="DAILY">Täglich</option>
                    <option value="WEEKLY">Wöchentlich</option>
                    <option value="MONTHLY">Monatlich</option>
                    <option value="WEEKDAYS">Wochentage</option>
                </select>
                <span class="recurrence-interval-field">
                    <label>alle <input type="number" name="interval" value="1" min="1"> ×</label>
                </span>
                <span class="recurrence-weekday-fields" hidden>
                    <label><input type="checkbox" name="weekday" value="MONDAY">Mo</label>
                    <label><input type="checkbox" name="weekday" value="TUESDAY">Di</label>
                    <label><input type="checkbox" name="weekday" value="WEDNESDAY">Mi</label>
                    <label><input type="checkbox" name="weekday" value="THURSDAY">Do</label>
                    <label><input type="checkbox" name="weekday" value="FRIDAY">Fr</label>
                    <label><input type="checkbox" name="weekday" value="SATURDAY">Sa</label>
                    <label><input type="checkbox" name="weekday" value="SUNDAY">So</label>
                </span>
                <button type="submit">Übernehmen</button>
            </form>
        </g:else>
    </details>
</div>
