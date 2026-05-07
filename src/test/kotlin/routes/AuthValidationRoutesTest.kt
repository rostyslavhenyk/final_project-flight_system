package routes

import auth.UserSession
import data.AllTables
import data.UserRepository
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import testsupport.withClue
import utils.SessionUtils
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthValidationRoutesTest {
    @BeforeTest
    fun setUpDatabase() {
        val dbFile = Files.createTempFile("auth-validation-test-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*AllTables.all())
        }
    }

    @Test
    fun `signup requires first name before creating an account`() =
        testApplication {
            configureAuthValidationRoutes()

            val response =
                client.submitForm(
                    url = "/signup",
                    formParameters =
                        Parameters.build {
                            append("firstname", "")
                            append("lastname", "Lovelace")
                            append("email", "ada@example.com")
                            append("password", "password")
                        },
                )

            withClue("missing first name returns the specific validation message") {
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("Please fill in a first name"))
            }
            withClue("invalid signup does not create a user") {
                assertEquals(null, UserRepository.getByEmail("ada@example.com"))
            }
        }

    @Test
    fun `signup checks required fields in order`() =
        testApplication {
            configureAuthValidationRoutes()

            withClue("last name is required after first name is present") {
                val response =
                    client.submitForm(
                        url = "/signup",
                        formParameters =
                            Parameters.build {
                                append("firstname", "Ada")
                                append("lastname", " ")
                                append("email", "ada@example.com")
                                append("password", "password")
                            },
                    )
                assertTrue(response.bodyAsText().contains("Please fill in a last name"))
            }
            withClue("email is required after names are present") {
                val response =
                    client.submitForm(
                        url = "/signup",
                        formParameters =
                            Parameters.build {
                                append("firstname", "Ada")
                                append("lastname", "Lovelace")
                                append("email", " ")
                                append("password", "password")
                            },
                    )
                assertTrue(response.bodyAsText().contains("Please fill in an email"))
            }
            withClue("password is required after name and email are present") {
                val response =
                    client.submitForm(
                        url = "/signup",
                        formParameters =
                            Parameters.build {
                                append("firstname", "Ada")
                                append("lastname", "Lovelace")
                                append("email", "ada@example.com")
                                append("password", " ")
                            },
                    )
                assertTrue(response.bodyAsText().contains("Please fill in a password"))
            }
        }

    @Test
    fun `signup rejects duplicate mandatory email`() =
        testApplication {
            configureAuthValidationRoutes()
            createUser("ada@example.com")

            val response =
                client.submitForm(
                    url = "/signup",
                    formParameters =
                        Parameters.build {
                            append("firstname", "Ada")
                            append("lastname", "Lovelace")
                            append("email", "ADA@example.com")
                            append("password", "password")
                        },
                )

            withClue("duplicate emails are caught after normalization") {
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("User already exists with that email"))
            }
        }

    @Test
    fun `login missing or blank mandatory credentials returns generic failure`() =
        testApplication {
            configureAuthValidationRoutes()
            createUser("ada@example.com", password = "correct-password")

            withClue("missing password is rejected") {
                val response =
                    client.submitForm(
                        url = "/login",
                        formParameters = Parameters.build { append("email", "ada@example.com") },
                    )
                assertTrue(response.bodyAsText().contains("Incorrect email or password"))
            }
            withClue("blank password is rejected") {
                val response =
                    client.submitForm(
                        url = "/login",
                        formParameters =
                            Parameters.build {
                                append("email", "ada@example.com")
                                append("password", "")
                            },
                    )
                assertTrue(response.bodyAsText().contains("Incorrect email or password"))
            }
        }

    @Test
    fun `forgot password and verification routes require mandatory fields`() =
        testApplication {
            configureAuthValidationRoutes()

            withClue("forgot password send requires an email or phone") {
                val response = client.submitForm("/forgot-password/send", Parameters.Empty)
                assertTrue(response.bodyAsText().contains("Please enter an email or phone number"))
            }
            withClue("forgot password verify requires an account key") {
                val response =
                    client.submitForm(
                        url = "/forgot-password/verify",
                        formParameters = Parameters.build { append("code", "123456") },
                    )
                assertTrue(response.bodyAsText().contains("Email or phone is required"))
            }
            withClue("forgot password verify requires the code") {
                val response =
                    client.submitForm(
                        url = "/forgot-password/verify",
                        formParameters = Parameters.build { append("email", "ada@example.com") },
                    )
                assertTrue(response.bodyAsText().contains("Please enter the code"))
            }
            withClue("password reset requires a new password") {
                val response =
                    client.submitForm(
                        url = "/forgot-password/reset",
                        formParameters = Parameters.build { append("email", "ada@example.com") },
                    )
                assertTrue(response.bodyAsText().contains("Please enter a new password"))
            }
        }

    @Test
    fun `email and sms verification endpoints require identifiers and codes`() =
        testApplication {
            configureAuthValidationRoutes()

            withClue("send email code requires email") {
                val response = client.submitForm("/verify/send-email", Parameters.Empty)
                assertTrue(response.bodyAsText().contains("Email is required"))
            }
            withClue("check email code requires both email and code") {
                val response =
                    client.submitForm(
                        url = "/verify/check-email",
                        formParameters = Parameters.build { append("email", "ada@example.com") },
                    )
                assertTrue(response.bodyAsText().contains("Email and code are required"))
            }
            withClue("send sms code requires phone") {
                val response = client.submitForm("/verify/send-sms", Parameters.Empty)
                assertTrue(response.bodyAsText().contains("Phone number is required"))
            }
            withClue("check sms code requires both phone and code") {
                val response =
                    client.submitForm(
                        url = "/verify/check-sms",
                        formParameters = Parameters.build { append("phone", "+447700900123") },
                    )
                assertTrue(response.bodyAsText().contains("Phone and code are required"))
            }
        }

    private fun createUser(
        email: String,
        password: String = "password",
    ) {
        UserRepository.add(
            firstname = "Ada",
            lastname = "Lovelace",
            roleId = CUSTOMER_ROLE_ID,
            email = email,
            password = BCrypt.hashpw(password, BCrypt.gensalt()),
        )
    }
}

private fun io.ktor.server.testing.ApplicationTestBuilder.configureAuthValidationRoutes() {
    application {
        install(Sessions) {
            cookie<SessionUtils>("SESSION")
            cookie<UserSession>("USER_SESSION")
        }
        routing {
            signUpRoutes()
            logInRoutes()
            verificationRoutes()
        }
    }
}

private const val CUSTOMER_ROLE_ID = 0
