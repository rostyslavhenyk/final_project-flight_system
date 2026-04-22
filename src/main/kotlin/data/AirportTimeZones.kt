package data

import java.time.ZoneId

/**
 * IATA → [ZoneId] for converting schedule instants to **local** wall times at each airport.
 * Uses real region rules (e.g. Europe/London for BST/GMT, Asia/Hong_Kong for HKT).
 */
object AirportTimeZones {
    fun zoneIdForIata(code: String): ZoneId {
        return when (code.uppercase()) {
            "MAN", "LBA", "LHR", "LGW", "EDI" -> ZoneId.of("Europe/London")
            "CDG" -> ZoneId.of("Europe/Paris")
            "AMS" -> ZoneId.of("Europe/Amsterdam")
            "BCN" -> ZoneId.of("Europe/Madrid")
            "FCO" -> ZoneId.of("Europe/Rome")
            "IST" -> ZoneId.of("Europe/Istanbul")
            "FRA", "MUC" -> ZoneId.of("Europe/Berlin")
            "ZRH" -> ZoneId.of("Europe/Zurich")
            "CPH" -> ZoneId.of("Europe/Copenhagen")
            "HKG" -> ZoneId.of("Asia/Hong_Kong")
            "BKK" -> ZoneId.of("Asia/Bangkok")
            "DPS" -> ZoneId.of("Asia/Makassar")
            "SIN" -> ZoneId.of("Asia/Singapore")
            "DXB", "AUH" -> ZoneId.of("Asia/Dubai")
            "DOH" -> ZoneId.of("Asia/Qatar")
            "NRT" -> ZoneId.of("Asia/Tokyo")
            "ICN" -> ZoneId.of("Asia/Seoul")
            "SYD" -> ZoneId.of("Australia/Sydney")
            "LAX" -> ZoneId.of("America/Los_Angeles")
            "JFK" -> ZoneId.of("America/New_York")
            "YVR" -> ZoneId.of("America/Vancouver")
            "DEL" -> ZoneId.of("Asia/Kolkata")
            "KUL" -> ZoneId.of("Asia/Kuala_Lumpur")
            else -> ZoneId.of("UTC")
        }
    }
}
