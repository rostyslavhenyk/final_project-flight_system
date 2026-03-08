package utils

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import isHtmxRequest

val ReqIdKey = AttributeKey<String>("rid")
val RequestStartTimeKey = AttributeKey<Long>("request_start")
private val SidKey = AttributeKey<String>("sid")

suspend fun <T> ApplicationCall.timed(
    taskCode: String,
    jsMode: String,
    block: suspend ApplicationCall.() -> T,
): T {
    val session = ensureSession()
    val sid = ensureSidCookie(session)
    attributes.put(SidKey, sid)
    val requestId = ensureRequestId()
    val started = System.currentTimeMillis()
    attributes.put(RequestStartTimeKey, started)

    return try {
        val result = block()
        val duration = System.currentTimeMillis() - started
        Logger.success(sid, requestId, taskCode, duration, jsMode)
        result
    } catch (e: RuntimeException) {
        val duration = System.currentTimeMillis() - started
        val (step, defaultStatus) =
            when (e) {
                is IllegalArgumentException -> "client_error" to HttpStatusCode.BadRequest.value
                else -> "server_error" to HttpStatusCode.InternalServerError.value
            }

        Logger.write(
            sessionId = sid,
            requestId = requestId,
            taskCode = taskCode,
            step = step,
            outcome = e::class.simpleName ?: "error",
            durationMs = duration,
            statusCode = response.status()?.value ?: defaultStatus,
            jsMode = jsMode,
        )
        throw e
    }
}

fun ApplicationCall.logValidationError(
    taskCode: String,
    outcome: String,
) {
    Logger.validationError(sessionCode(), requestId(), taskCode, outcome, jsMode())
}

fun ApplicationCall.jsMode(): String = if (isHtmxRequest()) "on" else "off"

fun ApplicationCall.sessionCode(): String = attributes[SidKey]

fun ApplicationCall.requestId(): String = attributes[ReqIdKey]

private fun ApplicationCall.ensureSession(): SessionData =
    sessions.get<SessionData>() ?: SessionData().also {
        sessions.set(it)
    }

private fun ApplicationCall.ensureRequestId(): String {
    val existing = attributes.getOrNull(ReqIdKey)
    if (existing != null) return existing
    val generated = newReqId()
    attributes.put(ReqIdKey, generated)
    return generated
}

private fun ApplicationCall.ensureSidCookie(session: SessionData): String {
    request.cookies["sid"]?.let { return it }
    val sid = shortSessionId(session.id)
    response.cookies.append(
        Cookie(
            name = "sid",
            value = sid,
            path = "/",
            httpOnly = false,
        ),
    )
    return sid
}
