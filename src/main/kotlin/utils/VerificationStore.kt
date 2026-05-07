package utils

import java.time.Instant

// stores codes temporarily while the server is running
// maps email or phone number to a code and expiry time
object VerificationStore {
    private const val CODE_MIN = 100_000
    private const val CODE_MAX = 999_999
    private const val CODE_EXPIRY_SECONDS = 600L

    private val codes = mutableMapOf<String, Pair<String, Instant>>()

    fun generateCode(): String = (CODE_MIN..CODE_MAX).random().toString()

    fun saveCode(
        key: String,
        code: String,
    ) {
        val expiry = Instant.now().plusSeconds(CODE_EXPIRY_SECONDS)
        codes[key] = Pair(code, expiry)
    }

    fun verifyCode(
        key: String,
        submittedCode: String,
    ): Boolean {
        val entry = codes[key] ?: return false
        val expired = Instant.now().isAfter(entry.second)
        if (expired) {
            codes.remove(key)
        }
        return !expired && entry.first == submittedCode
    }

    fun removeCode(key: String) {
        codes.remove(key)
    }
}
