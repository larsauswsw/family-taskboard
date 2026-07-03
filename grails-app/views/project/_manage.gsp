<div id="project-manage">
    <g:if test="${error}">
        <p class="project-error">${error}</p>
    </g:if>
    <ul class="project-list">
        <g:each in="${projects}" var="p">
            <li>
                <form hx-post="${createLink(controller: 'project', action: 'update', id: p.id)}"
                      hx-target="#project-manage" hx-swap="outerHTML">
                    <input type="text" name="name" value="${p.name}" required>
                    <input type="color" name="color" value="${p.color}">
                    <button type="submit">Speichern</button>
                </form>
                <button type="button"
                        hx-post="${createLink(controller: 'project', action: 'delete', id: p.id)}"
                        hx-target="#project-manage" hx-swap="outerHTML">Löschen</button>
            </li>
        </g:each>
    </ul>
    <form hx-post="${createLink(controller: 'project', action: 'create')}"
          hx-target="#project-manage" hx-swap="outerHTML">
        <input type="text" name="name" placeholder="Neues Projekt…" required>
        <input type="color" name="color" value="#3B82F6">
        <button type="submit">Anlegen</button>
    </form>
</div>
