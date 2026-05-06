package utils

import java.util.UUID

data class SessionUtils(
    val id: String = UUID.randomUUID().toString(),
)

fun shortSessionId(fullId: String): String = fullId.take(6)

fun generateRequestId(): String = "r_${UUID.randomUUID().toString().take(8)}"

fun newReqId(): String = generateRequestId()
