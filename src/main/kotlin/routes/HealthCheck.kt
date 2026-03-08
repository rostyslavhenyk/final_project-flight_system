package routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

/**
 * Health check endpoint for monitoring server status.
 *
 * **Use cases**:
 * - Codespaces port forwarding verification
 * - Load balancer health probes
 * - Smoke testing after deployment
 *
 * **Response**: JSON with status and timestamp
 */
fun Routing.configureHealthCheck() {
    get("/health") {
        call.respondText(
            """
            {
              "status": "ok",
              "service": "COMP2850 HCI Task Manager",
              "timestamp": "${System.currentTimeMillis()}",
              "version": "1.0-SNAPSHOT"
            }
            """.trimIndent(),
            ContentType.Application.Json,
        )
    }
}
