package data.flight

import data.AirportRepository
import java.util.Locale

private const val MAX_AIRPORT_CODE_LENGTH = 10
private const val IATA_CODE_LENGTH = 3

internal object AirportCodeResolver {
    fun resolve(raw: String): String? {
        val cleaned = raw.trim()
        return if (cleaned.isBlank()) {
            null
        } else {
            codeFromLabel(cleaned) ?: codeFromDirectInput(cleaned) ?: codeFromAirportName(cleaned)
        }
    }

    private fun codeFromLabel(cleaned: String): String? =
        Regex("\\(([A-Za-z0-9]{2,$MAX_AIRPORT_CODE_LENGTH})\\)")
            .find(cleaned)
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.UK)

    private fun codeFromDirectInput(cleaned: String): String? {
        val direct = cleaned.uppercase(Locale.UK)
        val airportCodeInput = cleaned.length in 2..MAX_AIRPORT_CODE_LENGTH && cleaned.all { it.isLetterOrDigit() }
        val codeExists = AirportRepository.all().any { it.code.equals(direct, ignoreCase = true) }
        return if (airportCodeInput && (codeExists || direct.length == IATA_CODE_LENGTH)) {
            direct
        } else {
            null
        }
    }

    private fun codeFromAirportName(cleaned: String): String? {
        val lowered = cleaned.lowercase(Locale.UK)
        return AirportRepository
            .all()
            .firstOrNull { airport ->
                val city = airport.city.lowercase(Locale.UK)
                val name = airport.name.lowercase(Locale.UK)
                city == lowered || name == lowered || city.startsWith(lowered) || name.startsWith(lowered)
            }?.code
            ?.uppercase(Locale.UK)
    }
}
