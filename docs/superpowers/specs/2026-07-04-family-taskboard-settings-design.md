# Family Taskboard — Settings-Seite (API-Token) — Design-Spezifikation

- **Datum:** 2026-07-04
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Behebt eine seit Phase 1 offene Lücke (`docs/apple-shortcut.md` §1:
  "There is no settings page for this yet ... for now, read it directly from
  the database").

## 1. Überblick & Ziel

Eine neue Seite `/settings` zeigt den eigenen Benutzernamen und API-Token
(read-only, zum Kopieren) sowie einen "Token neu generieren"-Button mit
Bestätigungs-Warnhinweis. Ein Link dazu erscheint in der bestehenden Navbar
auf der Task-Seite.

### Scope-Abgrenzung

**In Scope:**
- Eigenen Benutzernamen + API-Token anzeigen.
- API-Token neu generieren (mit Bestätigung).
- Navbar-Link zur neuen Seite.
- `docs/apple-shortcut.md` §1 aktualisieren: der Hinweis "There is no
  settings page for this yet" durch eine Anleitung ersetzen, den Token über
  `/settings` abzurufen, statt per SQL-Query.

**Explizit nicht in Scope** (separates Thema laut Nutzer-Entscheidung):
- Benachrichtigungs-Einstellungen (`User.notifyDaysBefore`/`notifyOnDueDate`)
  editierbar machen — existieren bereits als Felder, aber ohne UI; bewusst
  für später zurückgestellt.
- Ändern von Benutzername/Anzeigename/Passwort.
- Verwaltung anderer Benutzer (Single-Family-Architektur, keine Admin-Rolle).

## 2. Datenmodell

Keine Änderung an `User` — `apiToken` existiert bereits
(`nullable: true, unique: true`).

Neuer Service `UserService` (folgt dem etablierten Muster von
`ProjectService`/`TaskService`):

```groovy
package taskboard

import grails.gorm.transactions.Transactional
import java.security.SecureRandom

@Transactional
class UserService {

    private static final String TOKEN_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    private static final int TOKEN_LENGTH = 8
    private static final SecureRandom RANDOM = new SecureRandom()

    User regenerateApiToken(User user) {
        user.apiToken = (1..TOKEN_LENGTH).collect { TOKEN_CHARS[RANDOM.nextInt(TOKEN_CHARS.size())] }.join()
        user.save(failOnError: true)
        user
    }
}
```

**Token-Format bewusst kurz gewählt (8 alphanumerische Zeichen statt der
bisherigen 36-Zeichen-UUID):** einfacher manuell abzutippen. Dies ist ein
explizit akzeptierter Trade-off gegen geringere Entropie (~47 Bit statt ~122
Bit) — angemessen für eine private, selbst gehostete Familien-App ohne
öffentliche Angriffsfläche im üblichen Sinn. `SecureRandom` wird trotz der
kurzen Länge verwendet, da es sich weiterhin um ein Bearer-Credential
handelt. Bestehende, länger UUID-basierte Tokens anderer/derselben User
bleiben gültig — `apiToken` hat keine Formatbeschränkung, nur neu generierte
Tokens bekommen das kurze Format.

## 3. `SettingsController`

```groovy
package taskboard

import org.springframework.security.core.context.SecurityContextHolder

/** Own-account settings: view API token, regenerate it. No @Secured
 *  annotation -- covered by SecurityConfig's anyRequest().authenticated(),
 *  same as TaskController/ProjectController. */
class SettingsController {

    static defaultAction = 'show'

    UserService userService

    private User currentUser() {
        (User) SecurityContextHolder.context.authentication.principal
    }

    def show() {
        [user: currentUser()]
    }

    def regenerateToken() {
        userService.regenerateApiToken(currentUser())
        redirect action: 'show'
    }
}
```

Grails' bestehende Catch-all-Route (`/$controller/$action?/$id?` in
`UrlMappings.groovy`) deckt `/settings` (→ `show()` via `defaultAction`) und
`/settings/regenerateToken` bereits ab — keine Änderung an `UrlMappings.groovy`
nötig. `regenerateToken()` folgt dem Redirect-after-POST-Muster (vermeidet
Doppel-Ausführung bei Seiten-Reload).

## 4. UI

`grails-app/views/settings/show.gsp` — eine eigene, vollständige Seite (kein
HTMX-Fragment, da Settings kein Teil des Task-Listen-SPA-Systems ist):

```gsp
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Einstellungen</title>
    <asset:stylesheet src="taskboard.css"/>
</head>
<body>
    <header class="navbar">
        <h1>Einstellungen</h1>
        <a href="${createLink(controller: 'task')}">← Zurück</a>
    </header>

    <main class="settings-page">
        <p><strong>Benutzername:</strong> ${user.username}</p>
        <p><strong>API-Token:</strong></p>
        <input type="text" readonly value="${user.apiToken}" class="token-display"
               onclick="this.select()">

        <form method="post" action="${createLink(action: 'regenerateToken')}"
              onsubmit="return confirm('Der alte Token wird sofort ungültig. Bestehende Apple-Shortcuts funktionieren erst wieder nach Aktualisierung. Fortfahren?')">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <button type="submit">Token neu generieren</button>
        </form>
    </main>
</body>
</html>
```

**Bewusst kein HTMX/`hx-confirm` hier:** die Settings-Seite ist ein
Full-Page-Reload-Kontext, kein Fragment-Swap wie die Task-Liste. Ein
serverseitig gerendertes Formular mit nativem Browser-`confirm()`-Dialog
per `onsubmit` genügt und vermeidet, den HTMX-CSRF-Shim aus `index.gsp` auf
eine zweite Seite duplizieren zu müssen. Das `_csrf`-Hidden-Field folgt dem
exakt gleichen Muster wie das bestehende Login-Formular.

**Navbar-Link** in `grails-app/views/task/index.gsp`: ein `⚙️`-Link neben
"Meine Tasks", der auf `${createLink(controller: 'settings')}` zeigt.

## 5. Fehlerbehandlung & Edge Cases

- **Token-Kollision beim Regenerieren:** bei 8 alphanumerischen Zeichen
  (62⁸ ≈ 218 Billionen Kombinationen) praktisch ausgeschlossen; nicht
  speziell behandelt, `save(failOnError: true)` würde im (praktisch nie
  eintretenden) Kollisionsfall eine Exception werfen — gleiche
  Risikoakzeptanz wie beim bestehenden UUID-Seeding in `BootStrap.groovy`.
- **User ohne bisherigen Token** (`apiToken` ist `nullable: true`): die Seite
  zeigt ein leeres Eingabefeld; "Token neu generieren" legt in diesem Fall
  den ersten Token an — kein Sonderfall in der Logik nötig.
- **Unauthentifizierter Zugriff auf `/settings`:** bereits durch
  `SecurityConfig`s `anyRequest().authenticated()` abgedeckt (nicht in der
  `permitAll`-Liste) — Redirect zu Login, keine Config-Änderung nötig.
- **CSRF:** `regenerateToken()` ist ein session-basiertes POST, durch das
  bestehende CSRF-Setup automatisch geschützt (gleiches Verhalten wie beim
  Login-Formular).

## 6. Tests

- **Integration:** `UserServiceIntegrationSpec` — `regenerateApiToken`
  ersetzt den Token (neuer Wert ≠ alter Wert), neuer Token ist genau 8
  Zeichen lang und rein alphanumerisch.
- **Integration, HTTP-Flow:** `SettingsControllerIntegrationSpec` (Muster
  wie `ProjectControllerIntegrationSpec`) — unauthentifizierter GET auf
  `/settings` redirected zu Login; eingeloggter GET zeigt Benutzername +
  aktuellen Token; POST auf `/settings/regenerateToken` ändert den in der DB
  gespeicherten Token, und die anschließend gerenderte Seite zeigt den neuen
  Wert (nicht mehr den alten).

## 7. Offene Punkte / Annahmen

- Benachrichtigungs-Einstellungen (`notifyDaysBefore`/`notifyOnDueDate`)
  bleiben vorerst ohne UI — eigenes, späteres Thema.
- Keine Bestätigung per E-Mail o.ä. beim Regenerieren — passend zur
  Single-Family-Architektur ohne Multi-Faktor-Anspruch.
