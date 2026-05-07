package testsupport

inline fun withClue(
    clue: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (error: AssertionError) {
        throw AssertionError("$clue: ${error.message}", error)
    }
}
