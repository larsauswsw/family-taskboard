# Family Taskboard — Natural-Language-Datums-Parsing — Design-Spezifikation

- **Datum:** 2026-07-03
- **Repository:** https://github.com/larsauswsw/family-taskboard
- **Status:** Entwurf zur Umsetzung freigegeben
- **Bezug:** Aufbauend auf [2026-06-30-family-taskboard-design.md](2026-06-30-family-taskboard-design.md)
  §7/§12/§13, wo dieses Feature explizit als spätere Ausbaustufe des
  Quick-Add-Textparsings angekündigt wird ("Optionales, einfaches
  Natural-Language-Parsing für Datumsangaben (\"bis Freitag\", \"morgen\") --
  minimal in Phase 1, erweiterbar").

## 1. Überblick & Ziel

Der Quick-Add-Text (sowohl im Web-UI-Feld als auch im per Apple-Kurzbefehl
diktierten REST-Text) kann eine deutsche Datumsangabe enthalten, die bisher
ignoriert wird -- Tasks werden immer mit `dueDate = heute` angelegt, egal was
im Titel steht. Dieses Feature erkennt eine begrenzte, klar definierte Menge
an Formulierungen, extrahiert das Datum daraus, entfernt die erkannte Phrase
aus dem Titel und setzt `Task.dueDate` entsprechend.

### Scope-Abgrenzung

**In Scope:**
- `heute`, `morgen`, `übermorgen`.
- Wochentagsnamen (`Montag`...`Sonntag`, inkl. der Adverbform auf `-s`, z.B.
  `montags`) -- IMMER die nächste Vorkommnis **nach** heute, auch wenn heute
  bereits dieser Wochentag ist (dann: nächste Woche).
- Relative Angaben: `in N Tagen` / `in einem Tag`, `in N Wochen` /
  `in einer Woche`.
- Erkennung in beiden Quick-Add-Wegen: Web-UI-Titelfeld (inkl. per
  Spracheingabe diktierter Text) und der REST-Quick-Add-Endpunkt
  (`POST /api/tasks/quick`).
- Entfernen der erkannten Phrase (plus ein direkt davorstehendes `bis`/`am`/
  `in`) aus dem Titel.

**Explizit nicht in Scope:**
- Beliebige/komplexe NLP-Bibliotheken (siehe Architekturentscheidung unten).
- Mehrsprachigkeit (nur Deutsch).
- Wiederkehrende Tasks -- eine Formulierung wie `montags` erzeugt KEINE
  Wiederholungsregel, sondern wird identisch zu `Montag` behandelt (nächster
  Montag, einmalig).
- Mehrere Datumsangaben im selben Text kombinieren/validieren -- bei mehreren
  Treffern gewinnt schlicht der am weitesten links stehende.

## 2. Architektur

Ein neuer, zustandsloser `DateParsingService` (`grails-app/services/taskboard/
DateParsingService.groovy`, per Grails' `*Service`-Namenskonvention als
Spring-Bean registriert, siehe `DueDateReminderJobService`'s Docblock für die
Herleitung dieser Regel) mit einer einzigen Methode:

```
ParsedTitle parse(String title, LocalDate today)
```

`today` wird als Parameter übergeben statt intern `LocalDate.now()`
aufzurufen -- exakt das gleiche Muster wie `UrgencyService.colorFor(task,
today)` --, damit der Parser deterministisch und ohne Anhängigkeit von der
echten Systemzeit testbar ist.

`ParsedTitle` ist eine kleine, nicht-persistente Wertklasse (`src/main/groovy/
taskboard/ParsedTitle.groovy`, analog zu `Priority`/`TaskStatus` als
Nicht-Domänenklasse unter `src/main/groovy`):

```
class ParsedTitle {
    LocalDate date   // null, wenn keine Phrase erkannt wurde
    String title     // bereinigter Titel, oder unverändert, wenn date == null
}
```

**Warum kein externes NLP-/Datums-Parsing-Framework:** Gängige
Java-Bibliotheken für Datums-Parsing aus Fließtext (z.B. Natty) sind
englischsprachig; eine leichtgewichtige deutschsprachige Alternative
existiert nicht ohne eine schwere Abhängigkeit oder einen separaten
NLP-Dienst. Für die hier definierte, bewusst kleine Formulierungsmenge ist
ein handgeschriebener, regelbasierter Parser (Regex mit Wortgrenzen) die
richtige Größenordnung.

## 3. Integration in bestehende Aufrufer

`TaskService.createTask(Map params, User creator)` ist bereits die von
beiden Quick-Add-Wegen gemeinsam genutzte Stelle (siehe eigener Docblock:
"Used identically by TaskController.quickAdd() and ApiTaskController.quick()").
Neue Regel dort: **nur parsen, wenn kein explizites `dueDate` übergeben
wurde.** Ist `params.dueDate` gesetzt, bleibt das Verhalten unverändert
(expliziter Wert gewinnt immer). Ist es nicht gesetzt, ruft `createTask`
`dateParsingService.parse(params.title, LocalDate.now())` auf und verwendet
das erkannte Datum (oder heute, falls nichts erkannt wurde) sowie den
bereinigten Titel.

Das erfordert eine Anpassung an beiden Aufrufstellen, da beide aktuell
**immer** ein konkretes `dueDate` übergeben und damit das Parsing sonst
stillschweigend verhindern würden:

- `ApiTaskController.quick()`: übergibt aktuell `dueDate: LocalDate.now()`
  explizit -- muss künftig gar kein `dueDate` mehr übergeben, damit
  `createTask` entscheiden kann.
- `TaskController.quickAdd()`: übergibt aktuell `dueDate: params.dueDate ?
  LocalDate.parse(params.dueDate) : LocalDate.now()` -- der `: LocalDate.now()`
  -Fallback muss zu `: null` werden, damit ein fehlendes Formularfeld
  bedeutet "lass `createTask` parsen" statt "heute annehmen". Da das
  Web-Quick-Add-Formular aktuell ohnehin kein Datumsfeld absendet, geht in
  der Praxis jeder Web-Quick-Add durch das Parsing -- wird später ein
  echtes Datumsfeld ergänzt, gewinnt eine explizite Auswahl weiterhin über
  jede Datumsangabe im Text.

## 4. Fehlerbehandlung & Edge Cases

- **Keine Phrase gefunden:** `date == null`, Titel unverändert -->
  `createTask` fällt auf heute zurück -- exakt das heutige Verhalten.
- **Entfernen der Phrase würde einen leeren Titel ergeben** (z.B. der Text
  ist nur "morgen"): `Task.title` hat die Constraint `blank: false`, daher
  darf das Entfernen niemals einen leeren Titel erzeugen. In diesem Fall
  behält der Parser den **vollständigen Originaltext als Titel**, liefert
  aber trotzdem das erkannte Datum zurück -- ein redundanter Titel ist
  besser als ein fehlgeschlagener Speichervorgang.
- **Mehrere Phrasen im selben Text:** der am weitesten links stehende
  Treffer gewinnt -- nicht "intelligent", aber vorhersagbar und einfach.
- **Groß-/Kleinschreibung:** alle Muster werden case-insensitiv erkannt
  ("MORGEN", "Morgen", "morgen").
- **Wortgrenzen:** Muster werden nur als ganze Wörter erkannt (Regex
  `\b`-Grenzen), damit z.B. "Morgenlauf" nicht fälschlich als "morgen"
  erkannt wird.
- **"in 0 Tagen":** löst sich zu heute auf (`plusDays(0)` ist ein No-op),
  keine Sonderbehandlung nötig.

## 5. Tests

- **Unit** (`DateParsingServiceSpec`, reine Logik, deterministischer
  `today`-Parameter, keine DB): `heute`/`morgen`/`übermorgen`, jeder
  Wochentagsname inkl. des Falls "heutiger Wochentag bedeutet nächste
  Woche", `in N Tagen`, `in einer Woche`/`in N Wochen`, Durchreichen ohne
  Treffer, Case-Insensitivität, Wortgrenzen (`Morgenlauf` wird nicht
  erkannt), und der Fallback bei leerem Titel nach dem Entfernen.
- **Integration:** ein paar Fälle in `TaskServiceIntegrationSpec`, die die
  `createTask`-Verdrahtung belegen (explizites `dueDate` gewinnt weiterhin;
  fehlendes `dueDate` löst Parsing aus). Ein Fall in
  `ApiTaskControllerIntegrationSpec` mit dem Original-Beispiel aus der
  Spec -- `"Steuererklärung abgeben bis Freitag"` --, der den REST-Weg
  Ende-zu-Ende belegt.

## 6. Offene Punkte / Annahmen

- Keine Unterstützung für ausgeschriebene Zahlwörter über "einer"/"einem"
  hinaus (z.B. "in zwei Wochen" wird nicht erkannt, nur "in 2 Wochen") --
  passend zur bewusst kleinen Formulierungsmenge aus §1.
- `Samstag`/`Sonnabend` als Synonym wird nicht gesondert behandelt -- nur
  `Samstag` wird erkannt, da regional variierende Synonyme nicht Teil der
  aktuellen Anforderung sind. Bei Bedarf später ergänzbar.
