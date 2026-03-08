package auth

data class LoggedInState(
    val logged_in: Boolean,
    val session: UserSession?,
)
