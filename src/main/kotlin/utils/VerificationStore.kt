package utils

import java.time.Instant

// stores codes temporarily while the server is running
// maps email or phone number to a code and expiry time
object VerificationStore {
    private val codes = mutableMapOf<String, Pair<String, Instant>>()

    fun generateCode(): String = (100000..999999).random().toString()

    fun saveCode(
        key: String,
        code: String,
    ) {
        val expiry = Instant.now().plusSeconds(600)
        codes[key] = Pair(code, expiry)
    }

    fun verifyCode(
        key: String,
        submittedCode: String,
    ): Boolean {
        val entry = codes[key]
        if (entry == null) return false

        val savedCode = entry.first
        val expiry = entry.second

        if (Instant.now().isAfter(expiry)) {
            codes.remove(key)
            return false
        }

        return savedCode == submittedCode
    }

    fun removeCode(key: String) {
        codes.remove(key)
    }
}
