package auth

data class LoggedInState(
    val loggedIn: Boolean,
    val session: UserSession?,
)
