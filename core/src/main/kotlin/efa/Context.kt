package efa

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** CX2: a place is named and has its own point, radius and Wi-Fi names. */
enum class PlaceKind { FLAT, FAMILY_HOME, CAMPUS, LIBRARY, CHURCH, PITCH, SHOP, TRAIN, ELSEWHERE }

data class Place(val id: String, val name: String, val kind: PlaceKind, val lat: Double, val lon: Double, val radiusM: Int = 150, val wifi: Set<String> = emptySet())

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0; val p = Math.PI / 180
    val a = sin((lat2 - lat1) * p / 2).pow(2) + cos(lat1 * p) * cos(lat2 * p) * sin((lon2 - lon1) * p / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** CX4: "home" is whichever of your homes you are at. A signal is strong when GPS and Wi-Fi agree (FB7). */
data class PlaceFix(val place: Place?, val confidence: Double)

fun placeAt(lat: Double?, lon: Double?, wifi: String?, places: List<Place>): PlaceFix {
    val byWifi = wifi?.let { w -> places.firstOrNull { w in it.wifi } }
    val byGps = if (lat != null && lon != null) places.filter { distanceM(lat, lon, it.lat, it.lon) <= it.radiusM }.minByOrNull { distanceM(lat, lon, it.lat, it.lon) } else null
    return when {
        byWifi != null && byGps != null && byWifi.id == byGps.id -> PlaceFix(byGps, 1.0)
        byGps != null && byWifi == null -> PlaceFix(byGps, 0.8)
        byWifi != null && byGps == null -> PlaceFix(byWifi, 0.6)
        byWifi != null && byGps != null -> PlaceFix(byGps, 0.5)     // they disagree: lower confidence, GPS preferred
        else -> PlaceFix(null, 0.0)
    }
}

/** CX3: the day type, proposed from the calendar and place and confirmed once. */
enum class DayType { UNI, LIBRARY, HOME_STUDY, REST, TRAVEL, EXAM, DEADLINE, MATCH, CHURCH, GUESTS, ILL, LITE, HOLIDAY, OUT }

/** HO6: the University of Birmingham's real term dates for 2026-27 (undergraduate), read from its pages on 18 Nov 2026. */
enum class TermState { WELCOME, TEACHING, SUPPORT_WEEK, GRADUATIONS, VACATION, ASSESSMENT, OTHER }

data class TermSpan(val from: LocalDate, val to: LocalDate, val state: TermState, val label: String)

val UOB_2026_27: List<TermSpan> = listOf(
    TermSpan(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 25), TermState.WELCOME, "Welcome Week"),
    TermSpan(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 11, 1), TermState.TEACHING, "Autumn term"),
    TermSpan(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), TermState.SUPPORT_WEEK, "Teaching, academic and assessment support week"),
    TermSpan(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 11), TermState.TEACHING, "Autumn term"),
    TermSpan(LocalDate.of(2026, 12, 12), LocalDate.of(2027, 1, 10), TermState.VACATION, "Winter vacation"),
    TermSpan(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 16), TermState.ASSESSMENT, "January assessment period"),
    TermSpan(LocalDate.of(2027, 1, 17), LocalDate.of(2027, 2, 21), TermState.TEACHING, "Spring term"),
    TermSpan(LocalDate.of(2027, 2, 22), LocalDate.of(2027, 2, 26), TermState.SUPPORT_WEEK, "Teaching, academic and assessment support week"),
    TermSpan(LocalDate.of(2027, 2, 27), LocalDate.of(2027, 3, 28), TermState.TEACHING, "Spring term"),
    TermSpan(LocalDate.of(2027, 3, 29), LocalDate.of(2027, 4, 25), TermState.VACATION, "Spring vacation"),
    TermSpan(LocalDate.of(2027, 4, 26), LocalDate.of(2027, 5, 2), TermState.TEACHING, "Summer term"),
    TermSpan(LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 7), TermState.SUPPORT_WEEK, "Teaching, academic and assessment support week"),
    TermSpan(LocalDate.of(2027, 5, 8), LocalDate.of(2027, 6, 6), TermState.ASSESSMENT, "Main summer assessment period"),
    TermSpan(LocalDate.of(2027, 6, 7), LocalDate.of(2027, 6, 25), TermState.TEACHING, "Teaching and UoB Xtra"),
)

fun termOn(date: LocalDate, calendar: List<TermSpan> = UOB_2026_27): TermSpan? = calendar.firstOrNull { !date.isBefore(it.from) && !date.isAfter(it.to) }

/** Winter vacation as the university states it: Mon 14 Dec 2026 to Fri 8 Jan 2027. */
val WINTER_VACATION_FROM: LocalDate = LocalDate.of(2026, 12, 14)
val WINTER_VACATION_TO: LocalDate = LocalDate.of(2027, 1, 8)

data class DayProposal(val type: DayType, val reason: String)

/**
 * CX3: the day type for [date], from where you are, what is on the calendar and the term. Order: an exam; a day you travel; Christmas; church; a match with no lectures;
 * lectures (a uni day); a library period; a home or vacation day with no lectures; the weekend. The person confirms it once; they never assemble a day.
 */
fun proposeDayType(
    date: LocalDate, place: PlaceKind, yesterdayPlace: PlaceKind?, pattern: List<PatternSlot>, examToday: Boolean = false, libraryPeriod: Boolean = false,
    calendar: List<TermSpan> = UOB_2026_27, zone: java.time.ZoneId = LONDON,
): DayProposal {
    val slots = slotsFor(pattern, date, zone).filter { !it.slot.cancelled }
    val has = { t: String -> slots.any { it.slot.type == t } }
    val term = termOn(date, calendar)
    return when {
        examToday -> DayProposal(DayType.EXAM, "An exam is on the calendar.")
        yesterdayPlace != null && yesterdayPlace != place && (place == PlaceKind.FAMILY_HOME || yesterdayPlace == PlaceKind.FAMILY_HOME) -> DayProposal(DayType.TRAVEL, "You moved between your homes.")
        (date.monthValue == 12 && date.dayOfMonth == 25) -> DayProposal(DayType.HOLIDAY, "Christmas Day.")
        date.dayOfWeek == DayOfWeek.SUNDAY && has("church") -> DayProposal(DayType.CHURCH, "Church is on the calendar.")
        has("match") && !has("lecture") -> DayProposal(DayType.MATCH, "A match is on the calendar.")
        has("lecture") || has("tutorial") -> DayProposal(DayType.UNI, "Lectures are on the calendar.")
        libraryPeriod -> DayProposal(DayType.LIBRARY, "A library period is planned.")
        place == PlaceKind.FAMILY_HOME || term?.state == TermState.VACATION -> DayProposal(DayType.HOME_STUDY, if (term?.state == TermState.VACATION) "${term.label}: no lectures." else "You are at the family home and no lectures are on.")
        term?.state == TermState.SUPPORT_WEEK -> DayProposal(DayType.HOME_STUDY, "${term.label}: lectures may be lighter.")
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> DayProposal(DayType.REST, "The weekend.")
        else -> DayProposal(DayType.HOME_STUDY, "No lectures are on the calendar.")
    }
}

/** CX1: the adaptation phase of a routine (RT7). */
enum class Phase { OBSERVE, SUGGEST, ENFORCE }

fun phaseOf(daysSinceStart: Int): Phase = when { daysSinceStart < 7 -> Phase.OBSERVE; daysSinceStart < 14 -> Phase.SUGGEST; else -> Phase.ENFORCE }
