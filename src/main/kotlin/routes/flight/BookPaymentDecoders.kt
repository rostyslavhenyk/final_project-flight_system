package routes.flight

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val BASE64_BLOCK_SIZE = 4
private const val BASE64_PAD_CHAR = '='

private val paymentJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

internal fun decodeSeatSelection(raw: String?): Map<String, Map<String, Map<String, String>>> {
    val root = decodeBookingJson(raw)?.jsonObjectOrNull().orEmpty()
    return linkedMapOf<String, Map<String, Map<String, String>>>().apply {
        root["outbound"]?.jsonObjectOrNull()?.toJourneySeatMap()?.let { journey -> put("outbound", journey) }
        root["inbound"]?.jsonObjectOrNull()?.toJourneySeatMap()?.let { journey -> put("inbound", journey) }
    }
}

internal fun decodePaxDisplayNames(raw: String?): Map<Int, String> =
    decodeBookingJson(raw)
        ?.jsonArrayOrEmpty()
        ?.mapNotNull { passenger -> passenger.jsonObjectOrNull()?.toPassengerNameEntry() }
        ?.toMap(LinkedHashMap())
        .orEmpty()

private fun JsonObject.toJourneySeatMap(): Map<String, Map<String, String>> =
    entries
        .filter { (legKey) -> legKey.all { char -> char.isDigit() } }
        .associateTo(LinkedHashMap()) { (legKey, passengerMap) ->
            legKey to passengerMap.jsonObjectOrNull()?.toStringMap().orEmpty()
        }

private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associateTo(LinkedHashMap()) { (key, value) ->
        key to value.jsonPrimitive.content
    }

private fun JsonObject.toPassengerNameEntry(): Pair<Int, String>? {
    val slot = this["slot"]?.jsonPrimitive?.intOrNull
    val displayName = this["displayName"]?.jsonPrimitive?.contentOrNull
    return slot?.let { passengerSlot -> passengerSlot to displayName.orEmpty() }
}

private fun decodeBookingJson(raw: String?): JsonElement? {
    val json = decodeBase64Url(raw) ?: return null
    return runCatching { paymentJson.parseToJsonElement(json) }.getOrNull()
}

private fun decodeBase64Url(raw: String?): String? {
    val normalized = raw?.takeIf { value -> value.isNotBlank() }?.replace('-', '+')?.replace('_', '/')
    val padded = normalized?.padEnd(base64PaddedLength(normalized), BASE64_PAD_CHAR)
    return runCatching {
        String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
    }.getOrNull()
}

private fun base64PaddedLength(value: String): Int {
    val remainder = value.length % BASE64_BLOCK_SIZE
    val padding = (BASE64_BLOCK_SIZE - remainder) % BASE64_BLOCK_SIZE
    return value.length + padding
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

private fun JsonElement.jsonArrayOrEmpty(): List<JsonElement> = runCatching { jsonArray }.getOrDefault(emptyList())
