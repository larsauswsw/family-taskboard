<div class="task-card ${color}">
    <p class="task-title">${task.title}</p>
    <button class="complete-btn" hx-post="${createLink(action: 'complete', id: task.id)}"
            hx-target="#task-list" hx-swap="innerHTML" aria-label="Als erledigt markieren">✓</button>
    <div class="task-row-assign">
        <select name="project" class="project-select"
                style="background-color: ${task.project ? task.project.color : 'var(--color-pill-bg)'}; color: ${task.project ? '#fff' : 'var(--color-pill-text)'};"
                hx-post="${createLink(action: 'assignProject', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.project ? '' : 'selected'}>—</option>
            <g:each in="${projects}" var="p">
                <option value="${p.id}" ${task.project?.id == p.id ? 'selected' : ''}>${p.name}</option>
            </g:each>
        </select>
        <select name="assignedTo" class="assignee-select"
                hx-post="${createLink(action: 'assign', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="" ${task.assignedTo ? '' : 'selected'}>—</option>
            <g:each in="${users}" var="u">
                <option value="${u.id}" ${task.assignedTo?.id == u.id ? 'selected' : ''}>${u.displayName}</option>
            </g:each>
        </select>
    </div>
    <div class="task-row-facts">
        <input type="date" name="dueDate" class="date-select" value="${task.dueDate}"
               hx-post="${createLink(action: 'updateDueDate', id: task.id)}"
               hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
        <select name="priority" class="priority-select"
                hx-post="${createLink(action: 'updatePriority', id: task.id)}"
                hx-target="#task-list" hx-swap="innerHTML" hx-trigger="change">
            <option value="LOW" ${task.priority.name() == 'LOW' ? 'selected' : ''}>Niedrig</option>
            <option value="MEDIUM" ${task.priority.name() == 'MEDIUM' ? 'selected' : ''}>Mittel</option>
            <option value="HIGH" ${task.priority.name() == 'HIGH' ? 'selected' : ''}>Hoch</option>
            <option value="CRITICAL" ${task.priority.name() == 'CRITICAL' ? 'selected' : ''}>Kritisch</option>
        </select>
        <g:if test="${task.recurrenceRule?.active}">
            <span class="badge recur-chip" title="Wiederholt sich">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
            </span>
        </g:if>
        <g:else>
            <span></span>
        </g:else>
    </div>
    <details class="recurrence-details">
        <summary><svg class="recur-icon-inline" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>Wiederholung</summary>
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
