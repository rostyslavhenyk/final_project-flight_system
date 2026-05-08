package utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

object StripeService {
    private const val STRIPE_API_BASE_URL = "https://api.stripe.com/v1"
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX_EXCLUSIVE = 300

    private val secretKey = glideEnv("GLIDE_STRIPE_SECRET_KEY", "STRIPE_SECRET_KEY")
    val publishableKey: String = glideEnv("GLIDE_STRIPE_PUBLISHABLE_KEY", "STRIPE_PUBLISHABLE_KEY")
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun isConfigured(): Boolean = secretKey.startsWith("sk_test_") && publishableKey.startsWith("pk_test_")

    fun createSetupIntent(userId: Int): StripeSetupIntent? {
        if (!isConfigured()) return null
        val body =
            formBody(
                "payment_method_types[]" to "card",
                "usage" to "on_session",
                "metadata[user_id]" to userId.toString(),
            )
        val response = sendStripeRequest("POST", "$STRIPE_API_BASE_URL/setup_intents", body)
        return response?.takeIf { it.isOk() }?.body()?.let { responseBody ->
            val root = json.parseToJsonElement(responseBody).jsonObject
            StripeSetupIntent(
                id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                clientSecret = root["client_secret"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ).takeIf { intent -> intent.id.isNotBlank() && intent.clientSecret.isNotBlank() }
        }
    }

    fun setupIntentSucceeded(setupIntentId: String): Boolean {
        if (!isConfigured() || setupIntentId.isBlank()) return false
        val response = sendStripeRequest("GET", "$STRIPE_API_BASE_URL/setup_intents/$setupIntentId", null)
        val status =
            response
                ?.takeIf { it.isOk() }
                ?.body()
                ?.let { responseBody -> json.parseToJsonElement(responseBody).jsonObject }
                ?.get("status")
                ?.jsonPrimitive
                ?.contentOrNull
        return status == "succeeded"
    }

    private fun sendStripeRequest(
        method: String,
        url: String,
        body: String?,
    ): HttpResponse<String>? {
        val builder =
            HttpRequest
                .newBuilder(URI.create(url))
                .header("Authorization", "Basic ${basicAuthValue()}")
        val request =
            if (method == "POST") {
                builder
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.orEmpty()))
                    .build()
            } else {
                builder.GET().build()
            }
        return runCatching {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull()
    }

    private fun HttpResponse<String>.isOk(): Boolean = statusCode() in HTTP_OK_MIN until HTTP_OK_MAX_EXCLUSIVE

    private fun basicAuthValue(): String =
        Base64.getEncoder().encodeToString("$secretKey:".toByteArray(StandardCharsets.UTF_8))

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun glideEnv(
        preferredName: String,
        fallbackName: String,
    ): String =
        System.getenv(preferredName)?.takeIf { value -> value.isNotBlank() }
            ?: System.getenv(fallbackName).orEmpty()
}

data class StripeSetupIntent(
    val id: String,
    val clientSecret: String,
)
