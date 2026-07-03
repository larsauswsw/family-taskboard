package taskboard

import java.time.LocalDate

/** Result of DateParsingService.parse(): the extracted date (null if no
 *  recognized phrase was found) and the title with that phrase removed
 *  (unchanged if nothing was found, or if removing it would leave a blank
 *  title -- see DateParsingService for why). */
class ParsedTitle {
    LocalDate date
    String title
}
