package utils

import io.ktor.http.HttpStatusCode
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Log entry for interaction metrics.
 * Groups related logging parameters into a single data class.
 *
 * @property sessionId Session identifier
 * @property requestId Request identifier
 * @property taskCode Task code (e.g., T0_list, T3_add)
 * @property step Processing step (e.g., success, validation_error, server_error)
 * @property outcome Outcome description
 * @property durationMs Request duration in milliseconds
 * @property statusCode HTTP status code
 * @property jsMode JavaScript mode (on/off)
 */
data class LogEntry(
    val sessionId: String,
    val requestId: String,
    val taskCode: String,
    val step: String,
    val outcome: String,
    val durationMs: Long,
    val statusCode: Int,
    val jsMode: String,
)

// Week 9: writes interaction metrics for Task 1 instrumentation
object Logger {
    private val out =
        File("data/metrics.csv").apply {
            parentFile?.mkdirs()
            if (!exists()) writeText("ts_iso,session_id,request_id,task_code,step,outcome,ms,http_status,js_mode\n")
        }

    @Synchronized
    fun write(entry: LogEntry) {
        val ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        out.appendText(
            "$ts,${entry.sessionId},${entry.requestId},${entry.taskCode},${entry.step}," +
                "${entry.outcome},${entry.durationMs},${entry.statusCode},${entry.jsMode}\n",
        )
    }

    @Deprecated(
        "Use write(LogEntry) instead",
        ReplaceWith("write(LogEntry(sessionId, requestId, taskCode, step, outcome, durationMs, statusCode, jsMode))"),
    )
    @Synchronized
    fun write(
        sessionId: String,
        requestId: String,
        taskCode: String,
        step: String,
        outcome: String,
        durationMs: Long,
        statusCode: Int,
        jsMode: String,
    ) {
        write(
            LogEntry(
                sessionId = sessionId,
                requestId = requestId,
                taskCode = taskCode,
                step = step,
                outcome = outcome,
                durationMs = durationMs,
                statusCode = statusCode,
                jsMode = jsMode,
            ),
        )
    }

    fun validationError(
        sessionId: String,
        requestId: String,
        taskCode: String,
        outcome: String,
        jsMode: String,
    ) {
        write(sessionId, requestId, taskCode, "validation_error", outcome, 0, HttpStatusCode.BadRequest.value, jsMode)
    }

    fun success(
        sessionId: String,
        requestId: String,
        taskCode: String,
        durationMs: Long,
        jsMode: String,
    ) {
        write(sessionId, requestId, taskCode, "success", "", durationMs, HttpStatusCode.OK.value, jsMode)
    }
}
