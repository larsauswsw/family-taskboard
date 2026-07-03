package taskboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Extracts a German date phrase from free-form quick-add text (see design
 * spec docs/superpowers/specs/2026-07-03-family-taskboard-date-parsing-design.md).
 * Deterministic: takes `today` as a parameter rather than calling
 * LocalDate.now() internally, same pattern as UrgencyService.colorFor(task, today),
 * so callers/tests never depend on the real clock.
 *
 * Recognizes (case-insensitive, whole-word only via \b, with
 * UNICODE_CHARACTER_CLASS so ü/ö/ä count as word characters for boundary
 * purposes -- without it, Java's default \w/\b would misjudge the boundary
 * right before "übermorgen", since "ü" isn't in the default ASCII \w class):
 *   heute, morgen, übermorgen
 *   weekday names (Montag..Sonntag, plus the "-s" adverbial form e.g. montags) --
 *     always the NEXT occurrence strictly after `today`, even if today already is
 *     that weekday (then: next week, not today)
 *   "in N Tagen" / "in einem Tag", "in N Wochen" / "in einer Woche"
 *
 * If more than one phrase appears, the leftmost match wins (Matcher.find() on a
 * single combined alternation already returns the leftmost starting match).
 * The matched phrase (plus an immediately preceding "bis"/"am"/"in", if adjacent)
 * is removed from the title -- unless doing so would leave the title blank
 * (Task.title has blank:false), in which case the full original text is kept
 * as the title while still returning the parsed date.
 */
class DateParsingService {

    private static final Map<String, DayOfWeek> WEEKDAYS = [
        montag: DayOfWeek.MONDAY, dienstag: DayOfWeek.TUESDAY,
        mittwoch: DayOfWeek.WEDNESDAY, donnerstag: DayOfWeek.THURSDAY,
        freitag: DayOfWeek.FRIDAY, samstag: DayOfWeek.SATURDAY,
        sonntag: DayOfWeek.SUNDAY
    ]

    private static final Pattern PATTERN = Pattern.compile(
        '\\b(?<heute>heute)\\b' +
        '|\\b(?<uebermorgen>übermorgen)\\b' +
        '|\\b(?<morgen>morgen)\\b' +
        '|\\b(?<weekday>montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)s?\\b' +
        '|\\bin\\s+(?<reldays>\\d+)\\s+tag(?:en)?\\b' +
        '|\\b(?<eintag>in\\s+einem\\s+tag)\\b' +
        '|\\bin\\s+(?<relweeks>\\d+)\\s+woche(?:n)?\\b' +
        '|\\b(?<eineWoche>in\\s+einer\\s+woche)\\b',
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    )

    private static final Pattern LEADING_PREPOSITION =
        Pattern.compile('(?i)\\b(?:bis|am|in)\\s+$')

    ParsedTitle parse(String title, LocalDate today) {
        Matcher m = PATTERN.matcher(title)
        if (!m.find()) {
            return new ParsedTitle(date: null, title: title)
        }

        LocalDate date
        if (m.group('heute')) {
            date = today
        } else if (m.group('morgen')) {
            date = today.plusDays(1)
        } else if (m.group('uebermorgen')) {
            date = today.plusDays(2)
        } else if (m.group('weekday')) {
            date = nextWeekday(today, WEEKDAYS[m.group('weekday').toLowerCase()])
        } else if (m.group('reldays') != null) {
            date = today.plusDays(m.group('reldays') as long)
        } else if (m.group('eintag')) {
            date = today.plusDays(1)
        } else if (m.group('relweeks') != null) {
            date = today.plusWeeks(m.group('relweeks') as long)
        } else {
            date = today.plusWeeks(1) // eineWoche
        }

        int start = m.start()
        int end = m.end()
        Matcher prep = LEADING_PREPOSITION.matcher(title.substring(0, start))
        if (prep.find()) {
            start = prep.start()
        }

        String stripped = (title.substring(0, start) + title.substring(end))
            .replaceAll(/\s{2,}/, ' ').trim()

        new ParsedTitle(date: date, title: stripped ?: title)
    }

    private static LocalDate nextWeekday(LocalDate today, DayOfWeek target) {
        int daysToAdd = (target.value - today.dayOfWeek.value + 7) % 7
        if (daysToAdd == 0) daysToAdd = 7
        today.plusDays(daysToAdd)
    }
}
