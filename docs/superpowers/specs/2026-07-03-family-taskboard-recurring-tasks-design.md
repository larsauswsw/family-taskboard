# Family Taskboard — Wiederkehrende Tasks — Design-Spezifikation

- **Datum:** 2026-07-03
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Letzter offene Punkt aus dem ursprünglichen Phase-2-Bündel
  ([2026-06-30-family-taskboard-design.md](2026-06-30-family-taskboard-design.md)
  §12: "Wiederkehrende Tasks (nicht im aktuellen Scope)"). Projekte und
  erweitertes Datums-Parsing aus demselben Bündel sind bereits umgesetzt
  ([2026-07-03-family-taskboard-projects-design.md](2026-07-03-family-taskboard-projects-design.md),
  [2026-07-03-family-taskboard-date-parsing-design.md](2026-07-03-family-taskboard-date-parsing-design.md)).

## 1. Überblick & Ziel

Ein Task kann optional mit einer Wiederholungsregel verknüpft werden. Wird
ein solcher Task abgehakt, erzeugt die App automatisch den nächsten Task
derselben Serie mit passend berechnetem Fälligkeitsdatum. Unterstützte
Muster:

1. Fester Rhythmus: täglich / wöchentlich / monatlich, mit optionalem
   Intervall (z.B. "alle 2 Wochen").
2. Bestimmte Wochentage: z.B. "jeden Montag und Donnerstag".

### Scope-Abgrenzung

**In Scope:**
- `RecurrenceRule`-Domänenklasse (Typ, Intervall, Wochentage, aktiv/inaktiv).
- Task ↔ Wiederholungsregel-Zuordnung (optional).
- Automatische Erzeugung des nächsten Tasks beim Abhaken eines aktiven,
  wiederkehrenden Tasks.
- Inline-Konfiguration der Wiederholung auf der Task-Karte.
- "Serie beenden"-Aktion.

**Explizit nicht in Scope:**
- Wiederholung im Quick-Add-Formular festlegbar (nur nachträglich über die
  Karte, siehe §4).
- Vorschau/Übersicht kommender Termine einer Serie.
- Bearbeiten einer laufenden Regel (Typ/Intervall ändern) — nur
  Neuanlegen/Beenden.
- Verschieben/Nachholen einzelner verpasster Termine.

## 2. Datenmodell

```
RecurrenceRule
├── id
├── type       (enum: DAILY, WEEKLY, MONTHLY, WEEKDAYS)
├── interval   (Integer, >= 1, default 1 -- nur relevant für
│               DAILY/WEEKLY/MONTHLY; "alle 2 Wochen" = WEEKLY, interval=2)
├── weekdays   (String, nullable, z.B. "MONDAY,THURSDAY" -- nur relevant für
│               Typ WEEKDAYS; kommagetrennte DayOfWeek-Namen statt eigener
│               GORM-Join-Tabelle, da nur intern von RecurrenceService
│               geparst)
└── active     (boolean, default true)

Task (Ergänzung)
└── recurrenceRule → RecurrenceRule (optional, nullable, plain reference --
    kein GORM belongsTo/hasMany, gleicher Grund wie bei Project: eine Regel
    wird von allen Task-Instanzen derselben Serie geteilt und nie
    kaskadierend gelöscht, nur über `active` deaktiviert)
```

`weekdays` als String statt eigener Tabelle: die einzige Konsumentin ist
`RecurrenceService`, die die Liste beim Berechnen des nächsten Datums
parst — eine dedizierte Join-Tabelle für eine Handvoll Enum-Werte wäre
Overengineering für diese Größenordnung.

## 3. Erzeugungslogik

`TaskService.complete(Long id)` wird erweitert: nachdem der Task auf `DONE`
gesetzt wurde, prüft die Methode `task.recurrenceRule?.active`. Falls ja,
wird ein neuer `Task` mit denselben Werten (Titel, Priorität, `assignedTo`,
`project`, `recurrenceRule`, `createdBy`) und neu berechnetem
Fälligkeitsdatum angelegt. Der abgehakte Task selbst bleibt unverändert
(`DONE`, altes Datum) als Historieneintrag stehen.

Neuer, deterministischer Service `RecurrenceService.nextDueDate(RecurrenceRule
rule, LocalDate fromDate)` (gleiches Muster wie
`UrgencyService.colorFor(task, today)` / `DateParsingService.parse(title,
today)` — nimmt das Referenzdatum als Parameter statt intern
`LocalDate.now()` aufzurufen, dadurch uhrzeit-unabhängig testbar):

- `DAILY`: `fromDate.plusDays(interval)`
- `WEEKLY`: `fromDate.plusWeeks(interval)`
- `MONTHLY`: `fromDate.plusMonths(interval)` (Java klemmt automatisch auf
  den letzten gültigen Tag, z.B. 31. Jan + 1 Monat → 28./29. Feb — kein
  `DateTimeException`-Risiko)
- `WEEKDAYS`: kleinstes Datum > `fromDate`, das auf einen der in `weekdays`
  enthaltenen Wochentage fällt (analog zur `nextWeekday`-Formel aus
  `DateParsingService`, über mehrere Kandidaten-Wochentage minimiert)

**Basis ist immer das alte Fälligkeitsdatum**, nicht das Abhak-Datum — die
Serie bleibt im Takt, unabhängig davon, wann tatsächlich abgehakt wird.

## 4. UI

Jede Task-Karte bekommt einen einklappbaren `<details>`-Bereich "🔁
Wiederholung" (gleiches Muster wie der Projekt-Verwaltungsbereich):

- **Kein aktives Rule:** Formular mit `<select>` für den Typ
  (Keine/Täglich/Wöchentlich/Monatlich/Wochentage), einem
  Intervall-Zahlenfeld (nur bei Täglich/Wöchentlich/Monatlich sichtbar) und
  Wochentag-Checkboxen (nur bei Typ "Wochentage" sichtbar). Absenden per
  `hx-post` an `TaskController.setRecurrence(id)`, swap `#task-list`. Die
  bedingte Sichtbarkeit von Intervall-Feld vs. Wochentag-Checkboxen wird
  rein clientseitig per kleinem Inline-JS (`onchange` am Typ-Select)
  gesteuert, kein Server-Roundtrip vor dem Absenden.
- **Aktives Rule:** stattdessen eine kurze Zusammenfassung (z.B. "Wiederholt
  sich: wöchentlich") plus Button **"Serie beenden"** (`hx-post` an
  `TaskController.stopRecurrence(id)`, setzt `rule.active = false`).
- **Sichtbares Indiz ohne Aufklappen:** ein kleines 🔁-Badge neben
  Priorität/Datum, wenn `task.recurrenceRule?.active`.

Wiederholung ist erst *nach* dem Anlegen über die Karte konfigurierbar —
Quick-Add bleibt unverändert (siehe Scope-Abgrenzung). `setRecurrence` legt
bei jedem Absenden ein neues `RecurrenceRule` an (auch wenn der Task zuvor
schon eine gestoppte Regel hatte) — es gibt kein Wiederbeleben einer alten,
inaktiven Regel.

## 5. Fehlerbehandlung & Edge Cases

- **Bereits gestoppte Serie (`active = false`):** `complete()` erzeugt
  nichts — einfache Bedingung, kein Sonderfall.
- **"Serie beenden" wirkt für die ganze Serie:** alle Klone derselben Serie
  referenzieren dasselbe `RecurrenceRule`-Objekt, ein `active = false`
  reicht.
- **Ungültige Eingaben** (Intervall < 1, Typ WEEKDAYS ohne ausgewählten Tag):
  `RecurrenceService`/Controller lehnt ab, Liste wird unverändert neu
  gerendert — gleiches Muster wie `ProjectService.create()`, das bei
  ungültiger Eingabe `null` zurückgibt statt zu werfen.
- **`setRecurrence`/`stopRecurrence` auf nicht mehr existierende Task-Id:**
  gleicher defensiver `if (!t) return null`-Guard wie
  `complete()`/`assignTask()`/`assignProject()`.

## 6. Tests

Nach etabliertem Muster (Unit-Tests für reine Domänen-/Servicelogik,
Integrationstests für alles mit Spring/HTTP/DB-Bezug):

- **Unit:** `RecurrenceServiceSpec` — alle 4 Typen, verschiedene Intervalle,
  Wochentags-Auswahl (inkl. "wähle den nächsten passenden von mehreren
  Tagen"), Monatsende-Randfall (31. Jan + 1 Monat).
- **Integration:** `TaskServiceIntegrationSpec` — `complete()` einer aktiven
  Serie erzeugt einen neuen Task mit korrektem Datum/gleicher Regel;
  `complete()` einer gestoppten Serie erzeugt nichts; ein einmaliger
  (nicht wiederkehrender) Task verhält sich unverändert.
- **Integration, vollständiger HTTP-Flow:** anlegen → Wiederholung setzen
  → abhaken → neuer Task sichtbar → Serie beenden → erneut abhaken → keine
  weitere Klon-Erzeugung.

## 7. Offene Punkte / Annahmen

- Keine Bearbeitung einer laufenden Regel (Typ/Intervall ändern) — wer das
  Muster ändern will, beendet die Serie und legt eine neue Wiederholung an.
  Passt zur Single-Family-Größenordnung, kein Bedarf an komplexer
  Migrationslogik für laufende Serien.
- Kein Limit für die Gesamtzahl erzeugter Folge-Tasks einer Serie — jede
  Serie läuft, bis sie explizit beendet wird.
