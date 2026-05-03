package data.flight

import java.time.ZoneId
import java.time.ZoneOffset

object AirportTimeZoneResolver {
    fun offsetForIata(code: String): ZoneOffset =
        when (code.uppercase()) {
            "MAN", "LBA", "LHR", "LGW", "EDI", "DUB", "LIS" -> ZoneOffset.of("+00:00")
            "CDG", "AMS", "BCN", "FCO", "FRA", "MUC", "ZRH", "CPH" -> ZoneOffset.of("+01:00")
            "IST", "DOH" -> ZoneOffset.of("+03:00")
            "DXB", "AUH" -> ZoneOffset.of("+04:00")
            "DEL" -> ZoneOffset.of("+05:30")
            "BKK" -> ZoneOffset.of("+07:00")
            "HKG", "DPS", "SIN", "KUL" -> ZoneOffset.of("+08:00")
            "NRT", "ICN" -> ZoneOffset.of("+09:00")
            "SYD" -> ZoneOffset.of("+10:00")
            "JFK" -> ZoneOffset.of("-05:00")
            "LAX", "YVR" -> ZoneOffset.of("-08:00")
            else -> ZoneOffset.UTC
        }

    fun zoneIdForIata(code: String): ZoneId = ZoneId.ofOffset("UTC", offsetForIata(code))
}
