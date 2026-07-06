# Family Taskboard — Design-System-Fundament + Task-Liste — Design-Spezifikation

- **Datum:** 2026-07-04
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Erster Teil einer mehrteiligen visuellen Überarbeitung (bisher
  "barebones": `taskboard.css` hatte nur 22 Zeilen komponentenspezifischer
  Regeln, kein Basis-Styling). Folgeteile (jeweils eigene, spätere Specs):
  Projekt-Verwaltung, Settings-Seite, Login-Seite, Mic-Button-Aufnahme-Feedback.

## 1. Überblick & Ziel

Ein konsistentes Design-System ("Signalsystem": helles, kontrastreiches
Utility-Interface, in dem die bestehenden Dringlichkeitsfarben
grün→gelb→orange→rot→dunkelrot das dominante visuelle Signal bleiben) plus
eine überarbeitete Task-Listen-Seite, die Tasks nach Zeitraum gruppiert
(Überfällig / Heute / Diese Woche / Später) statt als eine flache Kette.

### Scope-Abgrenzung

**In Scope:**
- Farb-Token-System (hell + dunkel, siehe §2).
- Selbst gehostete Typografie: Inter (UI) + JetBrains Mono (Daten/Zahlen),
  siehe §3.
- Zeitraum-Gruppierung der Task-Liste, siehe §4.
- Konsistentes Komponenten-Styling (Karten, Pills, Chips, Badges, Buttons,
  Eingabefelder), siehe §5.
- Deutsche Prioritäts-Labels (`Priority.germanLabel`) statt Enum-Namen.
- Dark Mode: automatisch via `prefers-color-scheme` + zusätzlicher manueller
  Schalter (pro Browser, `localStorage`).

**Explizit nicht in Scope** (eigene, spätere Durchgänge):
- Projekt-Verwaltungs-Seite, Settings-Seite, Login-Seite — Re-Design folgt
  später, nutzt aber das hier geschaffene Token-System.
- Mic-Button-Aufnahme-Feedback (Stop-Icon während Aufnahme) — kommt danach,
  im neuen visuellen Stil.
- Neues User-Feld für eine account-gebundene Theme-Präferenz (Schalter bleibt
  bewusst pro Browser via `localStorage`).
- Änderung der bestehenden `UrgencyConfig`-Farbschwellen-Logik (`colorFor()`)
  — bleibt unverändert, die neue Zeitraum-Gruppierung ist rein zusätzlich.

## 2. Farb-Tokens

```css
:root {
  --color-bg: #F7F7F5;
  --color-surface: #FFFFFF;
  --color-text: #1A1A1A;
  --color-text-muted: #8A8A85;
  --color-pill-bg: #EAEAE7;
  --color-pill-text: #5A5A55;
  --color-pill-active-bg: #1A1A1A;
  --color-pill-active-text: #FFFFFF;
  --shadow-card: 0 1px 3px rgba(0,0,0,.05);

  --color-urgency-green: #3B8C3B;
  --color-urgency-yellow: #C79A1E;
  --color-urgency-orange: #D8752E;
  --color-urgency-red: #D9422E;
  --color-urgency-darkred: #7A1F1F;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg: #16181B;
    --color-surface: #212327;
    --color-text: #F0F0EE;
    --color-text-muted: #9A9A95;
    --color-pill-bg: #2A2C30;
    --color-pill-text: #C5C5C0;
    --color-pill-active-bg: #F0F0EE;
    --color-pill-active-text: #16181B;
    --shadow-card: 0 1px 3px rgba(0,0,0,.3);

    --color-urgency-green: #4CAF50;
    --color-urgency-yellow: #D9A62E;
    --color-urgency-orange: #E2864A;
    --color-urgency-red: #E5564A;
    --color-urgency-darkred: #C23B3B;
  }
}

/* Manueller Override -- gleiche Variablenwerte wie die jeweilige Media-Query,
   aber per Attribut statt Systemeinstellung ausgewählt. */
:root[data-theme="dark"] { /* identisch zum @media(dark)-Block oben */ }
:root[data-theme="light"] { /* identisch zum Standard-:root-Block oben */ }
```

## 3. Typografie

- **UI-Text:** Inter, selbst gehostet unter `grails-app/assets/fonts/inter/`,
  Schnitte 400 (normal), 600 (semibold), 700 (bold) — keine weiteren
  Schnitte.
- **Daten/Zahlen** (Fälligkeitsdatum, relative Tage): JetBrains Mono,
  Schnitt 400 — bewusster Kontrast als "Datenschrift".
- **Skala:** Seitentitel 22–24px/700 · Karten-Titel 15px/600 · Meta-Text
  11px/400 · Abschnitts-Label (Überfällig/Heute/…) 12px/700 mit
  `letter-spacing: 1.5px` + Großbuchstaben.
- Beide Schriften via `@font-face` mit `font-display: swap`, referenziert
  über Grails' `asset-url()`-CSS-Helper.

## 4. Layout — Zeitraum-Gruppierung

Neue Methode auf dem bestehenden `UrgencyService` (gleiches deterministische
Muster wie `colorFor(task, today, cfg)` — kein `LocalDate.now()` intern):

```groovy
String bucketFor(Task task, LocalDate today) {
    long daysOut = ChronoUnit.DAYS.between(today, task.dueDate)
    if (daysOut < 0) return "Überfällig"
    if (daysOut == 0) return "Heute"
    if (daysOut <= 6) return "Diese Woche"
    return "Später"
}
```

Die Gruppierung passiert rein auf Anzeige-Ebene in `_list.gsp` (Groovy
`.groupBy` auf die bereits vom Service geladene, nach Datum sortierte Liste)
— keine neue Datenbank-Query. Gilt einheitlich für die ungefilterte Liste
und jede Projekt-gefilterte Ansicht. Ein leerer Abschnitt wird komplett
ausgeblendet, keine leere Überschrift.

## 5. Komponenten

- **Karten:** `background: var(--color-surface)`, `border-radius: 12px`,
  `border-left: 5px solid <Dringlichkeitsfarbe>`, `box-shadow:
  var(--shadow-card)`.
- **Filter-Pills & Projekt-Chips:** vollständig rund (`border-radius:
  999px`). Pills nutzen `--color-pill-bg`/`--color-pill-active-bg`;
  Projekt-Chips behalten ihre nutzerdefinierte Hintergrundfarbe
  (`project.color`), nur Form/Radius wird angeglichen.
- **Prioritäts-Badge:** `background: var(--color-pill-bg)`,
  `border-radius: 6px`, 11px/400, zeigt `priority.germanLabel`.
- **Buttons** (Quick-Add „+“, Mic-FAB): rund/stark abgerundet, `background:
  var(--color-text)` mit invertierter Textfarbe.
- **Eingabefelder/Selects:** `border-radius: 8px`, 1px-Rahmen in
  `--color-pill-bg`, `background: var(--color-surface)`.

## 6. Prioritäts-Übersetzung

Neues Feld direkt am bestehenden `Priority`-Enum
(`src/main/groovy/taskboard/Priority.groovy`), analog zum vorhandenen
`multiplier`-Feld:

```groovy
enum Priority {
    LOW(1.0d, "Niedrig"), MEDIUM(1.2d, "Mittel"),
    HIGH(1.5d, "Hoch"), CRITICAL(2.0d, "Kritisch")
    final double multiplier
    final String germanLabel
    Priority(double m, String label) { this.multiplier = m; this.germanLabel = label }
}
```

Kein neuer Service, keine i18n-Message-Bundles — konsistent mit dem
bestehenden Stil der App (durchgängig hartkodierte deutsche Strings in den
GSPs).

## 7. Dark Mode

Automatisch via `prefers-color-scheme` (siehe §2). Zusätzlich ein manueller
Schalter (🌙/☀️-Button) direkt neben dem bestehenden ⚙️-Einstellungen-Link in
der Navbar (`task/index.gsp`), der `data-theme="dark"`/`"light"` auf `<html>` setzt
und den Wert in `localStorage` speichert; ein kleines Inline-Script liest
diesen Wert beim Laden und setzt das Attribut, bevor der erste Render
passiert (kein Flackern). Kein neues User-Domain-Feld — die Präferenz ist
bewusst pro Browser, nicht pro Account.

## 8. Dateien

- Neu: `grails-app/assets/stylesheets/_tokens.css` (Inhalt aus §2), per
  `//= require` am Anfang von `taskboard.css` eingebunden.
- Neu: `grails-app/assets/fonts/` mit Inter (400/600/700) und JetBrains Mono
  (400) als `.woff2`.
- Modify: `taskboard.css` (Komponenten-Regeln aus §5 statt der bisherigen
  Ad-hoc-Regeln).
- Modify: `grails-app/services/taskboard/UrgencyService.groovy`
  (`bucketFor()`).
- Modify: `src/main/groovy/taskboard/Priority.groovy` (`germanLabel`).
- Modify: `grails-app/views/task/_card.gsp` (`priority.germanLabel`).
- Modify: `grails-app/views/task/_list.gsp` (Gruppierung, Abschnitts-Header).
- Modify: `grails-app/views/task/index.gsp` (Dark-Mode-Schalter +
  Inline-Script, Font-Includes).

## 9. Tests

- Unit: `UrgencyServiceSpec` — `bucketFor()` an allen Grenzwerten (-1→Überfällig,
  0→Heute, 1 und 6→Diese Woche, 7→Später).
- Unit: `PrioritySpec` (neu) — `germanLabel` für alle 4 Werte.
- Rein visuelle/CSS-Änderungen werden nicht per Spock getestet, sondern
  manuell im Browser verifiziert (Muster aller bisherigen UI-Änderungen in
  diesem Projekt).

## 10. Fehlerbehandlung & Edge Cases

- Bestehende `colorFor()`-Logik bleibt unverändert — `bucketFor()` ist rein
  additiv.
- Der manuelle Dark-Mode-Schalter ist pro Browser (`localStorage`), nicht
  pro Account gespeichert.
- Ein Task exakt an der Grenze (`daysOut == 6`) landet in "Diese Woche"
  (inklusive), `daysOut == 7` in "Später" — Grenzwerte oben bereits explizit
  spezifiziert, kein Interpretationsspielraum.

## 11. Offene Punkte / Annahmen

- Projekt-Verwaltung, Settings-Seite, Login-Seite folgen als eigene, spätere
  Specs auf demselben Token-System.
- Mic-Button-Aufnahme-Feedback folgt danach, als eigener kleiner Durchgang.
