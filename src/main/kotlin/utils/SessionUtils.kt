package utils

import java.util.UUID

/**
 * Session data for tracking requests (privacy-safe).
 *
 * **Privacy by design**:
 * - ID is randomly generated UUID
 * - No PII stored (no names, emails, IPs)
 * - Used only for metrics correlation
 * - Cannot be linked to individuals
 *
 * **Usage**:
 * ```kotlin
 * val session = call.sessions.get<SessionData>() ?: SessionData().also {
 *     call.sessions.set(it)
 * }
 * Logger.write(session.id, ...)
 * ```
 *
 * @property id Unique session identifier (UUID)
 */
data class SessionData(
    val id: String = UUID.randomUUID().toString(),
)

/**
 * Generate a short session ID for logging (first 6 chars of UUID).
 * Used in console logs and CSV files for brevity.
 *
 * **Example**: `7a9f2c` instead of `7a9f2c3d-8b1e-4f5a-9c6d-2e1f3a4b5c6d`
 *
 * @param fullId Full UUID session ID
 * @return Short 6-character session ID
 */
fun shortSessionId(fullId: String): String = fullId.take(6)

/**
 * Generate a request ID for tracing individual requests within a session.
 * Used for detailed debugging and error tracking.
 *
 * **Format**: `r` + 8 random hex characters (e.g., `r_a3f7b2c1`)
 *
 * @return Request ID string
 */
fun generateRequestId(): String = "r_${UUID.randomUUID().toString().take(8)}"

fun newReqId(): String = generateRequestId()
