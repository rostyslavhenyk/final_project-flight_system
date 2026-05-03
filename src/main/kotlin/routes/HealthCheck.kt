package routes

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 * Lightweight endpoints for deployment probes.
 * Render can use `/healthz`; `/health` stays JSON for manual checks.
 */
fun Routing.configureHealthCheck() {
    get("/healthz") {
        call.respondText("ok", ContentType.Text.Plain)
    }

    get("/healthcheck") {
        call.respondText("ok", ContentType.Text.Plain)
    }

    get("/health") {
        call.respondText(
            """
            {
              "status": "ok",
              "service": "flight-system",
              "timestamp": "${System.currentTimeMillis()}",
              "version": "1.0-SNAPSHOT"
            }
            """.trimIndent(),
            ContentType.Application.Json,
        )
    }
}
