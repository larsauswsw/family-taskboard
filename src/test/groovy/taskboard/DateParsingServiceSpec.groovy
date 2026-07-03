package taskboard

import spock.lang.Specification
import java.time.DayOfWeek
import java.time.LocalDate

class DateParsingServiceSpec extends Specification {

    private static final Map<DayOfWeek, String> GERMAN_NAME = [
        (DayOfWeek.MONDAY): 'Montag', (DayOfWeek.TUESDAY): 'Dienstag',
        (DayOfWeek.WEDNESDAY): 'Mittwoch', (DayOfWeek.THURSDAY): 'Donnerstag',
        (DayOfWeek.FRIDAY): 'Freitag', (DayOfWeek.SATURDAY): 'Samstag',
        (DayOfWeek.SUNDAY): 'Sonntag'
    ]

    DateParsingService service = new DateParsingService()
    LocalDate today = LocalDate.of(2026, 7, 6)

    void "heute resolves to today and is stripped"() {
        when:
        def result = service.parse("Müll rausbringen heute", today)

        then:
        result.date == today
        result.title == "Müll rausbringen"
    }

    void "morgen resolves to today plus one day"() {
        when:
        def result = service.parse("Müll rausbringen morgen", today)

        then:
        result.date == today.plusDays(1)
        result.title == "Müll rausbringen"
    }

    void "übermorgen resolves to today plus two days and is not confused with morgen"() {
        when:
        def result = service.parse("Müll rausbringen übermorgen", today)

        then:
        result.date == today.plusDays(2)
        result.title == "Müll rausbringen"
    }

    void "MORGEN in upper case is still recognized"() {
        when:
        def result = service.parse("Müll rausbringen MORGEN", today)

        then:
        result.date == today.plusDays(1)
    }

    void "Morgenlauf is not mistaken for morgen (whole-word match only)"() {
        when:
        def result = service.parse("Morgenlauf vorbereiten", today)

        then:
        result.date == null
        result.title == "Morgenlauf vorbereiten"
    }

    void "Montagsmeeting is not mistaken for a weekday phrase (compound word)"() {
        when:
        def result = service.parse("Montagsmeeting vorbereiten", today)

        then:
        result.date == null
        result.title == "Montagsmeeting vorbereiten"
    }

    void "a weekday name matching today's own weekday resolves to next week, not today"() {
        given:
        String name = GERMAN_NAME[today.dayOfWeek]

        when:
        def result = service.parse("Termin ${name}".toString(), today)

        then:
        result.date == today.plusDays(7)
        result.title == "Termin"
    }

    void "a weekday name resolves to the next upcoming occurrence of that weekday"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Termin ${name}".toString(), today)

        then:
        result.date == target
        result.title == "Termin"
    }

    void "bis Freitag strips both 'bis' and the weekday name"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Steuererklärung abgeben bis ${name}".toString(), today)

        then:
        result.date == target
        result.title == "Steuererklärung abgeben"
    }

    void "am Montag strips both 'am' and the weekday name"() {
        given:
        LocalDate target = today.plusDays(2)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Zahnarzt am ${name}".toString(), today)

        then:
        result.date == target
        result.title == "Zahnarzt"
    }

    void "in 3 Tagen resolves to today plus three days"() {
        when:
        def result = service.parse("Paket abholen in 3 Tagen", today)

        then:
        result.date == today.plusDays(3)
        result.title == "Paket abholen"
    }

    void "in einem Tag resolves to today plus one day"() {
        when:
        def result = service.parse("Paket abholen in einem Tag", today)

        then:
        result.date == today.plusDays(1)
        result.title == "Paket abholen"
    }

    void "in einer Woche resolves to today plus seven days"() {
        when:
        def result = service.parse("Paket abholen in einer Woche", today)

        then:
        result.date == today.plusDays(7)
        result.title == "Paket abholen"
    }

    void "in 2 Wochen resolves to today plus fourteen days"() {
        when:
        def result = service.parse("Paket abholen in 2 Wochen", today)

        then:
        result.date == today.plusDays(14)
        result.title == "Paket abholen"
    }

    void "in 0 Tagen resolves to today (no special-casing needed)"() {
        when:
        def result = service.parse("Paket abholen in 0 Tagen", today)

        then:
        result.date == today
        result.title == "Paket abholen"
    }

    void "no recognized phrase leaves date null and title unchanged"() {
        when:
        def result = service.parse("Einfach nur ein Titel", today)

        then:
        result.date == null
        result.title == "Einfach nur ein Titel"
    }

    void "when the whole text is just the date phrase, the title is kept instead of left blank"() {
        when:
        def result = service.parse("morgen", today)

        then:
        result.date == today.plusDays(1)
        result.title == "morgen"
    }

    void "the leftmost of two phrases wins"() {
        given:
        LocalDate target = today.plusDays(3)
        String name = GERMAN_NAME[target.dayOfWeek]

        when:
        def result = service.parse("Termin ${name} oder übermorgen".toString(), today)

        then:
        result.date == target
    }
}
