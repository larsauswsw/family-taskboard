<div id="user-manage">
    <ul class="project-list">
        <g:each in="${users}" var="u">
            <li>
                <strong>${u.username}</strong>
                <form hx-post="${createLink(controller: 'userManagement', action: 'update', id: u.id)}"
                      hx-target="#user-manage" hx-swap="outerHTML">
                    <div class="field-row">
                        <input type="text" name="displayName" value="${u.displayName}" placeholder="Anzeigename" required>
                        <input type="email" name="email" value="${u.email ?: ''}" placeholder="E-Mail (optional)">
                    </div>
                    <input type="password" name="newPassword" placeholder="Neues Passwort (leer = unverändert)">
                    <label class="checkbox-field">
                        <input type="checkbox" name="admin" value="true" ${u.admin ? 'checked' : ''}>
                        Administrator
                    </label>
                    <div class="member-row-actions">
                        <button type="submit">Speichern</button>
                        <button type="button" class="btn-danger-outline"
                                hx-post="${createLink(controller: 'userManagement', action: 'delete', id: u.id)}"
                                hx-target="#user-manage" hx-swap="outerHTML"
                                hx-confirm="Nutzer &quot;${u.displayName}&quot; wirklich löschen?"
                                ${u.id == currentUserId ? 'disabled' : ''}>Löschen</button>
                    </div>
                </form>
            </li>
        </g:each>
    </ul>

    <g:if test="${error}">
        <p class="project-error">${error}</p>
    </g:if>

    <h2 class="section-heading">Neuen Nutzer anlegen</h2>
    <form hx-post="${createLink(controller: 'userManagement', action: 'create')}"
          hx-target="#user-manage" hx-swap="outerHTML" class="manage-create-form">
        <input type="text" name="username" placeholder="Benutzername" required>
        <div class="field-row">
            <input type="text" name="displayName" placeholder="Anzeigename" required>
            <input type="email" name="email" placeholder="E-Mail (optional)">
        </div>
        <input type="password" name="password" placeholder="Passwort (mind. 8 Zeichen)" required>
        <label class="checkbox-field">
            <input type="checkbox" name="admin" value="true">
            Administrator
        </label>
        <button type="submit">Anlegen</button>
    </form>
</div>
