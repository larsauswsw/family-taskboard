<div id="user-manage">
    <g:if test="${error}">
        <p class="project-error">${error}</p>
    </g:if>
    <ul class="project-list">
        <g:each in="${users}" var="u">
            <li>
                <form hx-post="${createLink(controller: 'userManagement', action: 'update', id: u.id)}"
                      hx-target="#user-manage" hx-swap="outerHTML">
                    <div><strong>${u.username}</strong></div>
                    <input type="text" name="displayName" value="${u.displayName}" placeholder="Anzeigename" required>
                    <input type="email" name="email" value="${u.email ?: ''}" placeholder="E-Mail (optional)">
                    <input type="password" name="newPassword" placeholder="Neues Passwort (leer = unverändert)">
                    <label>
                        <input type="checkbox" name="admin" value="true" ${u.admin ? 'checked' : ''}>
                        Administrator
                    </label>
                    <button type="submit">Speichern</button>
                </form>
                <button type="button"
                        hx-post="${createLink(controller: 'userManagement', action: 'delete', id: u.id)}"
                        hx-target="#user-manage" hx-swap="outerHTML"
                        hx-confirm="Nutzer &quot;${u.displayName}&quot; wirklich löschen?"
                        ${u.id == currentUserId ? 'disabled' : ''}>Löschen</button>
            </li>
        </g:each>
    </ul>
    <form hx-post="${createLink(controller: 'userManagement', action: 'create')}"
          hx-target="#user-manage" hx-swap="outerHTML">
        <input type="text" name="username" placeholder="Benutzername" required>
        <input type="text" name="displayName" placeholder="Anzeigename" required>
        <input type="email" name="email" placeholder="E-Mail (optional)">
        <input type="password" name="password" placeholder="Passwort (mind. 8 Zeichen)" required>
        <label>
            <input type="checkbox" name="admin" value="true">
            Administrator
        </label>
        <button type="submit">Anlegen</button>
    </form>
</div>
