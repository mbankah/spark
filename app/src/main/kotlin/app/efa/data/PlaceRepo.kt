package app.efa.data

import androidx.room.withTransaction
import efa.Building
import efa.Venues
import efa.coarseHome

/** One timetable venue as the setup screen shows it. */
data class VenueStatus(val venueText: String, val key: String, val point: Pair<Double, Double>?, val source: String)

/**
 * The home point and the venue points. The home point is the only personal place stored, at about 100 m resolution (L8, ST7).
 * It is set on a map, so no location is read to set it (A8). Venue points come from the OpenStreetMap directory unless the user
 * placed one; every placement is kept as a row, which is the correction log (L5).
 */
class PlaceRepo(private val db: EfaDb) {
    suspend fun home(): Pair<Double, Double>? {
        val lat = db.meta().get(META_HOME_LAT)?.toDoubleOrNull() ?: return null
        val lon = db.meta().get(META_HOME_LON)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    /** Saves the home point rounded to about 100 m and returns what was saved. */
    suspend fun setHome(lat: Double, lon: Double): Pair<Double, Double> {
        val p = coarseHome(lat, lon)
        db.withTransaction { db.meta().put(MetaRow(META_HOME_LAT, p.first.toString())); db.meta().put(MetaRow(META_HOME_LON, p.second.toString())) }
        return p
    }

    /** Directory key: the building alias when the directory knows the building, otherwise the venue text itself. */
    fun keyFor(venueText: String): String = Venues.find(venueText)?.alias ?: venueText

    suspend fun pointFor(venueText: String): Pair<Double, Double>? {
        val key = keyFor(venueText)
        db.venues().all().lastOrNull { it.venueKey == key }?.let { return Pair(it.lat, it.lon) }
        return Venues.find(venueText)?.let { Pair(it.lat, it.lon) }
    }

    suspend fun setVenuePoint(venueText: String, lat: Double, lon: Double, nowMs: Long) {
        db.venues().insert(VenuePointRow(venueKey = keyFor(venueText), lat = lat, lon = lon, atMs = nowMs, source = "user"))
    }

    /** Every timetable lecture venue with where its point comes from (L5). */
    suspend fun statuses(venueTexts: Collection<String>): List<VenueStatus> {
        val rows = db.venues().all()
        return venueTexts.distinct().sorted().map { v ->
            val key = keyFor(v)
            val user = rows.lastOrNull { it.venueKey == key }
            when {
                user != null -> VenueStatus(v, key, Pair(user.lat, user.lon), "placed by you")
                Venues.find(v) != null -> VenueStatus(v, key, Pair(Venues.find(v)!!.lat, Venues.find(v)!!.lon), "OpenStreetMap")
                else -> VenueStatus(v, key, null, "not found")
            }
        }
    }
}
