# Family Taskboard — Design-Spezifikation

- **Datum:** 2026-06-30
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben

## 1. Überblick & Ziel

Eine Webanwendung zur Aufgabenverwaltung für **eine Familie** (eine
Benutzergruppe mit mehreren Personen, keine echte Multi-Tenant-Plattform).
Kernziel: Tasks mit **minimalem Aufwand** anlegen — idealerweise per Sprache,
mit so wenigen Schritten wie möglich — und auf einen Blick erkennen, welche
Tasks dringend werden (Farbkodierung).

### Primäre Anforderungen

- Grails 7.1.1, in Docker lauffähig
- Tasks schnell per Sprache hinzufügen (iPhone + Apple Watch)
- Tasks haben ein Datum; je näher die Fälligkeit, desto „gefährlicher" die
  Farbe (grün → gelb → orange → rot → dunkelrot)
- Mehrere Benutzer (Familie), gemeinsame Datenbasis
- Tasks können optional Projekte haben (Phase 2)
- Push-Benachrichtigungen für iOS (auf der Watch gespiegelt)

## 2. Technologie-Stack

| Bereich | Wahl | Begründung |
|---------|------|-----------|
| Framework | Grails 7.1.1 | Vorgabe |
| Sprache/JDK | Java 21 (LTS) | Aktuelle LTS, kompatibel mit Grails 7 |
| Datenbank | PostgreSQL | Produktionsstabil, persistent |
| ORM | GORM (Hibernate) | Grails-Standard |
| UI | GSP + HTMX | Reaktive Interaktion ohne SPA-Komplexität |
| Auth | Spring Security (Username/Passwort) | Einfach, bewährt |
| Scheduler | Quartz Plugin | Geplante Benachrichtigungen |
| Push | Web Push API (VAPID) | Kein APNs-Zertifikat nötig |
| Spracheingabe (Web) | Web Speech API | Browser-nativ |
| Quick-Add (extern) | Apple Kurzbefehle → REST | Siri/Watch/Sperrbildschirm |
| Deployment | Docker Compose (app + db) | Vorgabe |

## 3. Architektur

```
┌──────────────────────────────────────────────┐
│              Docker Compose                    │
│                                               │
│  ┌────────────────────────┐   ┌────────────┐ │
│  │  Grails App (Tomcat)    │   │ PostgreSQL │ │
│  │  - Spring Security      │◄─►│  (Volume)  │ │
│  │  - GORM / Hibernate     │   └────────────┘ │
│  │  - GSP + HTMX           │                  │
│  │  - REST: /api/tasks/*   │                  │
│  │  - Quartz Scheduler     │                  │
│  │  - Web Push (VAPID)     │                  │
│  └────────────────────────┘                  │
└──────────────────────────────────────────────┘
        ▲                          ▲
        │ HTTPS                    │ HTTPS POST (+ API-Token)
        │                          │
   ┌─────────┐              ┌──────────────┐
   │   PWA    │              │ Apple        │
   │ (iPhone  │              │ Kurzbefehl   │
   │  Home-   │              │ (Siri/Watch/ │
   │  screen) │              │ Sperrbild.)  │
   └─────────┘              └──────────────┘
```

Zwei Eingangswege auf dasselbe Backend:

1. **PWA** — das „Zuhause" der App: Liste, Farben, Filter, Push-Empfang,
   Familienverwaltung, Komfort-Spracheingabe in der App.
2. **Apple Kurzbefehl** — der schnellste Quick-Add per Sprache über Siri,
   Apple Watch, Sperrbildschirm-Widget, Back-Tap oder Action-Button.

## 4. Datenmodell

```
User
├── id
├── username        (unique)
├── password        (bcrypt, via Spring Security)
├── displayName
├── email
├── apiToken        (für Kurzbefehl-Authentifizierung, pro User)
└── notifyPrefs     (z.B. "1 Tag vorher", "am Tag")

Task
├── id
├── title
├── dueDate
├── priority        (LOW, MEDIUM, HIGH, CRITICAL)
├── status          (OPEN, IN_PROGRESS, DONE)
├── description     (optional)
├── assignedTo      → User
├── createdBy       → User
├── project         → Project (optional, Phase 2)
├── lastNotifiedAt  (verhindert Doppel-Benachrichtigung)
├── dateCreated
└── lastUpdated

Project (Phase 2)
├── id
├── name
├── color
└── tasks           → Task (1:n)

UrgencyConfig        (Singleton pro Installation)
├── greenDaysThreshold     (Default 14)
├── yellowDaysThreshold    (Default 7)
├── orangeDaysThreshold    (Default 3)
├── redDaysThreshold       (Default 1)
└── priorityMultiplier     (Map: LOW=1.0, MEDIUM=1.2, HIGH=1.5, CRITICAL=2.0)

PushSubscription
├── id
├── endpoint
├── p256dh / auth   (Web-Push-Keys)
└── user            → User
```

## 5. Dringlichkeits- / Farblogik

Konfigurierbar über `UrgencyConfig`, mit Einfluss der Priorität:

```
effectiveDays = daysUntilDue / priorityMultiplier[task.priority]

effectiveDays > greenThreshold     → grün
effectiveDays > yellowThreshold    → gelb
effectiveDays > orangeThreshold    → orange
effectiveDays > redThreshold       → rot
effectiveDays <= redThreshold      → rot
daysUntilDue < 0 (überfällig)      → dunkelrot
```

Hochprioritäre Tasks werden durch den Multiplikator früher „gefährlich".
Beispiel: Ein CRITICAL-Task (Multiplikator 2.0), 6 Tage entfernt, hat
`effectiveDays = 3` und ist damit bereits orange, während ein LOW-Task bei
gleicher Frist noch gelb wäre.

- **Clientseitig** (JavaScript) für sofortige Farb-Updates ohne Reload.
- **Serverseitig** (Service-Methode) für Sortierung und Filterung.
- Die Logik lebt in einem `UrgencyService` und ist die zentrale, unit-getestete
  Kernlogik der Anwendung.

## 6. UI & Spracheingabe (PWA)

### Layout

- Mobile-first, responsive; iPhone ist primäres Gerät.
- Dark/Light Mode über CSS-Variablen.
- Task-Liste als Karten; **linker Rand farbkodiert** nach Dringlichkeit.
- Gruppierung nach Zeit (Heute / Diese Woche / Später).
- Filter nach Person, Status, Dringlichkeit.
- **FAB-Button** (Floating Action Button) unten rechts mit Mikrofon-Icon als
  primärer Quick-Add-Einstieg innerhalb der App (Mockup-Variante B).

### Interaktionen

| Geste | Aktion |
|-------|--------|
| Tap auf Karte | Task-Details öffnen |
| Long-Press auf Karte | Kontextmenü (erledigt / zuweisen / löschen / Datum) |
| Long-Press auf FAB | Push-to-talk: gedrückt sprechen, loslassen = anlegen |
| Swipe auf Karte | Schnell erledigt / löschen |

iOS-Long-Press-Konflikte (Textauswahl, Callout) werden per CSS unterdrückt
(`-webkit-touch-callout: none; user-select: none;`).

### Spracheingabe-Flow (in der App)

1. FAB tippen (oder gedrückt halten) → Web Speech API startet.
2. Gesprochener Text landet im Task-Titel-Feld.
3. HTMX submitted das Formular ohne Seitenreload.
4. Neuer Task erscheint sofort in der Liste.

### PWA-Eigenschaften

- `manifest.json` mit App-Icon, Name, `display: standalone`.
- Service Worker für Offline-Caching der App-Shell und Push-Empfang.
- Push-Permission wird nach dem ersten Login angefragt.

## 7. Schnellster Weg: Apple Kurzbefehl (Quick-Add)

Der primäre „schnellste Weg" zum Anlegen, da reine iOS-PWAs **keine**
App-Icon-Quick-Actions, Homescreen-Widgets oder Sperrbildschirm-Aktionen
unterstützen (Apple-Einschränkung). Die Apple-Kurzbefehle-App schließt diese
Lücke und bietet sogar mehr Trigger.

### Trigger (alle ohne App-Öffnen)

| Trigger | Schritte |
|---------|----------|
| „Hey Siri, Task hinzufügen" | 0 Taps — nur sprechen |
| Action-Button (iPhone 15 Pro+) | 1 Druck → sprechen |
| Back-Tap (2× Rückseite) | 0 Taps |
| Sperrbildschirm-Widget (Kurzbefehle) | 1 Tap → sprechen |
| Homescreen-Widget (Kurzbefehle) | 1 Tap → sprechen |
| Apple Watch (Siri / Kurzbefehl) | 0 Taps — am Handgelenk |

### Funktionsweise

Der Kurzbefehl diktiert Text und sendet ihn per HTTPS-POST an das Backend.
Der Kurzbefehl wird einmal eingerichtet und per iCloud auf alle Geräte
(inkl. Watch) verteilt.

### REST-Endpunkt

```
POST /api/tasks/quick
Header: Authorization: Bearer <apiToken>
Body:   { "text": "Steuererklärung abgeben bis Freitag" }

Antworten:
  201 Created      → { "id": 42, "title": "...", "dueDate": "..." }
  401 Unauthorized → ungültiges/fehlendes Token
  422 Unprocessable→ leerer Text
```

- Klare HTTP-Codes, weil der Kurzbefehl das Ergebnis als Siri-Rückmeldung
  vorliest.
- Optionales, einfaches Natural-Language-Parsing für Datumsangaben
  („bis Freitag", „morgen") — minimal in Phase 1, erweiterbar.

## 8. Benachrichtigungen & Scheduler

- **Quartz-Job** läuft stündlich, prüft offene Tasks, versendet fällige
  Erinnerungen über Web Push (VAPID).
- **Auslöser:**
  - **Zeitbasiert** — pro User konfigurierbar (z.B. „1 Tag vorher" + „am Tag").
    `lastNotifiedAt` verhindert Doppelversand.
  - **Zuweisung** — sofort beim Zuweisen an eine Person.
  - **Statusänderung** — bei Wechsel auf `DONE` wird der Ersteller informiert.
- **iOS-Voraussetzung:** Web Push funktioniert nur, wenn die PWA zum
  Homescreen hinzugefügt wurde. Auf der Apple Watch werden iPhone-Notifications
  gespiegelt (keine eigenständige PWA-Watch-Push).

## 9. Authentifizierung & Sicherheit

- Spring Security mit Username/Passwort (bcrypt).
- Pro User ein `apiToken` für den Kurzbefehl-Endpunkt (Bearer-Auth).
- Secrets (DB-Passwort, VAPID-Keys) über `.env` / Umgebungsvariablen,
  nicht im Repo.
- HTTPS in Produktion (Reverse Proxy vorgelagert, außerhalb dieses Scopes).

## 10. Deployment

- **Docker Compose**, zwei Services:
  - `app` — Grails-Anwendung (Tomcat).
  - `db` — PostgreSQL mit benanntem Volume für Persistenz.
- `.env` für Secrets und Konfiguration.
- VAPID-Keys werden einmalig generiert und als Env-Variablen eingebracht.

## 11. Fehlerbehandlung & Tests

- **Quick-Add-Endpunkt:** definierte HTTP-Codes (siehe §7).
- **Tests:**
  - Unit-Tests für `UrgencyService` (Farb-/Dringlichkeitslogik — Kernlogik).
  - Integrationstests für Controller und GORM (Task-CRUD, Quick-Add).
  - Smoke-Test für den Web-Push-Versand.

## 12. Scope-Abgrenzung

### Phase 1 (dieser Plan)

- User-Verwaltung (Familie), Auth.
- Task-CRUD mit Titel, Datum, Person, Priorität, Status.
- Farbkodierte Liste mit konfigurierbarer Dringlichkeitslogik.
- PWA mit Web-Speech-Spracheingabe + FAB.
- Apple-Kurzbefehl-Quick-Add-Endpunkt.
- Web-Push-Benachrichtigungen (zeitbasiert, Zuweisung, Statusänderung).
- Docker-Compose-Deployment.

### Phase 2 (später)

- Projekte (Gruppierung von Tasks, eigene Farbe).
- Erweitertes Natural-Language-Datums-Parsing.
- Wiederkehrende Tasks (nicht im aktuellen Scope).

## 13. Offene Punkte / Annahmen

- Natural-Language-Parsing im Quick-Add ist in Phase 1 minimal (Titel = Text,
  Datum optional). Bei Bedarf später ausbauen.
- Watch-Benachrichtigungen funktionieren über iPhone-Spiegelung; keine
  separate Watch-App geplant.
