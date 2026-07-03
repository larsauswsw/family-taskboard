# Family Taskboard — Phase 2: Projekte — Design-Spezifikation

- **Datum:** 2026-07-03
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Aufbauend auf [2026-06-30-family-taskboard-design.md](2026-06-30-family-taskboard-design.md)
  (Phase 1, abgeschlossen), §12 dort kündigt "Projekte (Gruppierung von Tasks,
  eigene Farbe)" für Phase 2 an.

## 1. Überblick & Ziel

Tasks können optional einem **Projekt** zugeordnet werden. Ein Projekt hat
einen Namen und eine Farbe und dient ausschließlich als Gruppierungs-Label —
keine Unterprojekte, keine eigenen Berechtigungen, keine Fristen auf
Projektebene. Erweiterung um zwei Bedienszenarien, die über die reine
Datenmodellierung aus der Phase-1-Spec hinausgehen:

1. Projekte anlegen/bearbeiten/löschen (bisher nur per Datenbankzugriff möglich).
2. Die Task-Liste nach Projekt filtern.

### Scope-Abgrenzung

**In Scope:**
- `Project`-Domänenklasse (Name, Farbe).
- Task ↔ Projekt-Zuordnung (optional, änderbar).
- Inline-Projektverwaltung auf der Task-Seite (Anlegen/Bearbeiten/Löschen).
- Projekt-Filter (Tabs/Pills) über der Task-Liste.
- Projekt-Auswahl im Quick-Add-Formular UND per Dropdown auf der Task-Karte.

**Explizit nicht in Scope** (unverändert aus Phase-1-Spec §12):
- Erweitertes Natural-Language-Datums-Parsing.
- Wiederkehrende Tasks.
- Unterprojekte, Projekt-Mitgliedschaften/-Berechtigungen, Projekt-Fristen.

## 2. Datenmodell

```
Project
├── id
├── name        (String, blank: false)
├── color       (String, Hex "#RRGGBB", regex-validiert)
└── (kein GORM hasMany/belongsTo zu Task -- siehe §3 zur Löschsemantik)

Task (Ergänzung zu Phase 1)
└── project     → Project (optional, nullable)
```

`Task.project` ist ein einfaches, unidirektionales Feld — **kein**
`belongsTo`/`hasMany`-Paar. Bei bidirektionaler GORM-Owner-Beziehung würde das
Löschen eines Projekts standardmäßig alle zugehörigen Tasks mitlöschen
(Cascade-Delete), was hier ausdrücklich falsch wäre: ein Projekt ist nur ein
Label, kein Besitzer der Tasks.

## 3. Löschsemantik

`ProjectService.delete(Project p)`:

1. Alle Tasks mit `task.project == p` finden und `task.project = null` setzen
   (explizit im Service, nicht über GORM-Cascade — gleicher expliziter Stil
   wie z.B. der handgeschriebene `AuthenticationProvider`-Bean in Phase 1).
2. Erst danach das Projekt selbst löschen.

Ergebnis: betroffene Tasks bleiben erhalten und erscheinen danach unter
"Kein Projekt". Es kann nie eine verwaiste Fremdschlüssel-Referenz entstehen,
da die Nullifizierung immer vor dem Löschen passiert.

## 4. UI

### Projektverwaltung (inline)

Da ein Projekt nur zwei editierbare Eigenschaften hat (Name, Farbe), lebt die
Verwaltung inline auf der Task-Seite in einem einklappbaren Bereich
("Projekte verwalten"), keine eigene Route:

- Liste bestehender Projekte, je mit Inline-Bearbeitung (Name-Textfeld,
  `<input type="color">`) und Löschen-Button.
- Ein kompaktes Formular zum Neuanlegen am Ende der Liste.
- Alles HTMX-basiert (`hx-post`/`hx-delete`), rendert nur diesen Abschnitt neu.

### Projekt-Filter

Reihe farbiger Pills über der Task-Liste: "Alle", "Kein Projekt", dann ein
Pill pro Projekt (Hintergrundfarbe = `project.color`). Klick auf ein Pill
lädt die gefilterte Liste per `hx-get` in `#task-list`; das aktive Pill wird
visuell hervorgehoben. Ein Filter auf ein zwischenzeitlich gelöschtes Projekt
fällt auf "Alle" zurück (siehe §5).

### Quick-Add

Ein Projekt-`<select>` neben dem Titel-Feld, standardmäßig "Kein Projekt",
optional.

### Task-Karte

- Kleiner farbiger Chip (Projektname auf `project.color`-Hintergrund) neben
  der bestehenden Zuweisen-Auswahl — ergänzt, ersetzt nicht den
  dringlichkeitsbasierten linken Rand.
- Zusätzliches Projekt-`<select>` (gleiches Inline-Änderungsmuster wie das
  bestehende Zuweisen-Dropdown aus Phase 1) zum nachträglichen Ändern des
  Projekts.

## 5. Fehlerbehandlung & Edge Cases

- **Validierung:** `name` darf nicht leer sein; `color` muss dem Muster
  `^#[0-9A-Fa-f]{6}$` entsprechen. Ungültige Eingabe rendert den
  Verwaltungsbereich mit Fehlermeldung neu, nichts wird verworfen.
- **Projekt mit Tasks löschen:** erlaubt, nicht blockiert (siehe §3).
- **Filter auf gelöschtes/ungültiges Projekt:** fällt auf "Alle" zurück statt
  zu fehlern — gleiches defensives Null-Muster wie
  `TaskService.complete`/`assignTask` (Phase-1-Review-Fixes).
- **Task-Projekt wird während der Zuordnung gelöscht:** keine verwaiste
  Referenz möglich, da Löschung immer zuerst nullifiziert.

## 6. Tests

Nach etabliertem Projektmuster (Unit-Tests für reine Domänen-/Servicelogik,
Integrationstests für alles mit Spring/HTTP/DB-Bezug):

- **Unit:** `Project`-Constraints (leerer Name abgelehnt, ungültiger
  Farb-Hex abgelehnt).
- **Integration:** `ProjectService` — Anlegen/Bearbeiten, und insbesondere
  `delete()` nullifiziert `project` auf referenzierenden Tasks vor dem
  Löschen des Projekts (das Verhalten mit dem größten Bug-Risiko).
- **Integration:** `TaskService`-Projekt-Filter-Query (gefiltert nach
  Projekt, "Kein Projekt", sowie unverändertes Verhalten bei fehlendem
  Filter).
- **Integration, vollständiger HTTP-Flow** (nach Muster von
  `TaskControllerSessionFlowIntegrationSpec`): Login → Projekt anlegen →
  Task per Quick-Add im Projekt anlegen → nach Projekt-Pill filtern → nur
  dieser Task erscheint → Projekt löschen → Task existiert weiterhin ohne
  Projekt-Chip.

## 7. Offene Punkte / Annahmen

- Keine Berechtigungsprüfung auf Projektebene — passend zur
  Single-Family-Architektur aus Phase 1 (§9 dort: keine echte
  Multi-Tenant-Plattform).
- Farbüberschneidung zwischen zwei Projekten wird nicht verhindert (kein
  Unique-Constraint auf `color`) — rein kosmetisch, kein funktionales
  Problem.
