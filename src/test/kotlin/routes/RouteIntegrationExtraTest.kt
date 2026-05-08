package routes

import auth.UserSession
import data.AllTables
import data.ChatRepository
import data.SeatMaintenance
import data.TicketRepository
import data.UserRepository
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
import routes.flight.flightsRoutes
import routes.staff.staffRoutes
import testsupport.withClue
import utils.SessionUtils
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteIntegrationExtraTest {
    @BeforeTest
    fun setUpDatabase() {
        val dbFile = Files.createTempFile("route-integration-extra-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*AllTables.all())
            SeatMaintenance.ensureUniqueSeatIndex()
        }
    }

    @Test
    fun `signup login protected page and logout flow uses sessions`() =
        testApplication {
            configureExtraRoutes()
            val client =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }

            val signup =
                client.submitForm(
                    url = "/signup",
                    formParameters =
                        Parameters.build {
                            append("firstname", "Ada")
                            append("lastname", "Lovelace")
                            append("email", "ada@example.com")
                            append("password", "password")
                        },
                )
            withClue("successful signup redirects through htmx") {
                assertEquals("/", signup.headers[HttpHeaders.Location] ?: signup.headers["HX-Redirect"])
            }

            val loggedInLoginPage = client.get("/login")
            withClue("logged-in users are redirected away from login") {
                assertEquals(HttpStatusCode.Found, loggedInLoginPage.status)
                assertEquals("/", loggedInLoginPage.headers[HttpHeaders.Location])
            }

            client.submitForm("/logout", Parameters.Empty)
            val failedLogin =
                client.submitForm(
                    url = "/login",
                    formParameters =
                        Parameters.build {
                            append("email", "ada@example.com")
                            append("password", "wrong")
                        },
                )
            withClue("wrong password does not establish a new session") {
                assertTrue(failedLogin.bodyAsText().contains("Incorrect email or password"))
                assertFalse((client.get("/login").headers[HttpHeaders.Location] ?: "").isNotBlank())
            }
        }

    @Test
    fun `login redirect parameter blocks unsafe targets`() =
        testApplication {
            configureExtraRoutes()
            createUser("safe@example.com", roleId = CUSTOMER_ROLE_ID)

            val externalRedirect = client.loginWithRedirect("https://example.com/steal")
            val protocolRelativeRedirect = client.loginWithRedirect("//example.com")
            val loginLoopRedirect = client.loginWithRedirect("/login?redirect=/staff")
            val safeRedirect = client.loginWithRedirect("/my-account")

            withClue("external redirects are replaced with home") {
                assertEquals("/", externalRedirect.redirectTarget())
            }
            withClue("protocol-relative redirects are replaced with home") {
                assertEquals("/", protocolRelativeRedirect.redirectTarget())
            }
            withClue("login-loop redirects are replaced with home") {
                assertEquals("/", loginLoopRedirect.redirectTarget())
            }
            withClue("safe local redirects are preserved") {
                assertEquals("/my-account", safeRedirect.redirectTarget())
            }
        }

    @Test
    fun `login keeps booking redirect from explicit parameter and safe referrer`() =
        testApplication {
            configureExtraRoutes()
            createUser("safe@example.com", roleId = CUSTOMER_ROLE_ID)

            val bookingRedirect = "/book/seats?from=MAN&to=AMS&depart=2026-05-07&fare=essential"
            val explicitRedirect = client.loginWithRedirect(bookingRedirect)
            val loginFromBooking =
                client.get("/login") {
                    header(HttpHeaders.Referrer, "http://localhost$bookingRedirect")
                }

            withClue("successful login preserves full booking redirect target") {
                assertEquals(bookingRedirect, explicitRedirect.redirectTarget())
            }
            withClue("login page falls back to the previous local page when no redirect is supplied") {
                val body = loginFromBooking.bodyAsText()
                assertTrue(body.contains("/book/seats?from=MAN"))
                assertTrue(body.contains("fare=essential"))
            }
        }

    @Test
    fun `staff routes reject customers and allow staff admin pages`() =
        testApplication {
            configureExtraRoutes()
            val customer =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }
            val admin =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }
            createUser("customer@example.com", roleId = CUSTOMER_ROLE_ID)
            createUser("admin@example.com", roleId = STAFF_ADMIN_ROLE_ID)

            customer.login("customer@example.com")
            admin.login("admin@example.com")

            withClue("ordinary customers are redirected out of staff routes") {
                val response = customer.get("/staff")
                assertEquals(HttpStatusCode.Found, response.status)
                assertEquals("/", response.headers[HttpHeaders.Location])
            }
            withClue("staff admin can open create staff page") {
                val response = admin.get("/staff/staff-accounts")
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("Create Staff"))
            }
        }

    @Test
    fun `chat json routes require login and return only the logged in conversation`() =
        testApplication {
            configureExtraRoutes()
            val firstClient = createClient { install(HttpCookies) }
            val secondClient = createClient { install(HttpCookies) }
            val first = createUser("first@example.com", roleId = CUSTOMER_ROLE_ID)
            val second = createUser("second@example.com", roleId = CUSTOMER_ROLE_ID)
            ChatRepository.add(first.id, "Support Team", "First reply", true)
            ChatRepository.add(second.id, "Support Team", "Second reply", true)

            withClue("anonymous chat message requests are unauthorized") {
                assertEquals(HttpStatusCode.Unauthorized, client.get("/chat/messages").status)
            }

            firstClient.login("first@example.com")
            secondClient.login("second@example.com")

            withClue("blank chat sends are rejected") {
                val response = firstClient.submitForm("/chat/send", Parameters.build { append("message", " ") })
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
            withClue("logged-in chat send is stored") {
                val response =
                    firstClient.submitForm(
                        "/chat/send",
                        Parameters.build { append("message", "Hello staff") },
                    )
                assertEquals(HttpStatusCode.OK, response.status)
            }
            withClue("chat messages are scoped to the logged-in account") {
                val firstBody = firstClient.get("/chat/messages").bodyAsText()
                val secondBody = secondClient.get("/chat/messages").bodyAsText()
                assertTrue(firstBody.contains("First reply"))
                assertTrue(firstBody.contains("Hello staff"))
                assertFalse(firstBody.contains("Second reply"))
                assertTrue(secondBody.contains("Second reply"))
            }
        }

    @Test
    fun `staff can close chat and customer message reopens it`() =
        testApplication {
            configureExtraRoutes()
            val customerClient = createClient { install(HttpCookies) }
            val staffClient = createClient { install(HttpCookies) }
            val customer = createUser("chat-customer@example.com", roleId = CUSTOMER_ROLE_ID)
            createUser("chat-staff@example.com", roleId = STAFF_ADMIN_ROLE_ID)

            customerClient.login("chat-customer@example.com")
            staffClient.login("chat-staff@example.com")
            customerClient.submitForm("/chat/send", Parameters.build { append("message", "Can you help?") })

            withClue("staff chat page shows the active conversation") {
                val body = staffClient.get("/staff/chat").bodyAsText()
                assertTrue(body.contains(customer.email))
                assertTrue(body.contains("Can you help?"))
            }

            val closeResponse =
                staffClient.submitForm(
                    "/staff/chat/close",
                    Parameters.build { append("userId", customer.id.toString()) },
                )

            withClue("closing chat redirects back to staff chat") {
                assertEquals(HttpStatusCode.Found, closeResponse.status)
                assertTrue(ChatRepository.isClosed(customer.id))
            }
            withClue("closed chat no longer appears in active staff conversations") {
                val body = staffClient.get("/staff/chat").bodyAsText()
                assertFalse(body.contains("Can you help?"))
            }

            customerClient.submitForm("/chat/send", Parameters.build { append("message", "I still need help") })

            withClue("a new customer message reopens the chat for staff") {
                val body = staffClient.get("/staff/chat").bodyAsText()
                assertFalse(ChatRepository.isClosed(customer.id))
                assertTrue(body.contains("I still need help"))
            }
        }

    @Test
    fun `help contact form creates user ticket visible to staff`() =
        testApplication {
            configureExtraRoutes()
            val customer = createUser("ticket-user@example.com", roleId = CUSTOMER_ROLE_ID)
            createUser("ticket-admin@example.com", roleId = STAFF_ADMIN_ROLE_ID)
            val admin =
                createClient {
                    followRedirects = false
                    install(HttpCookies)
                }

            val response =
                client.post("/help/tickets") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("firstname", customer.firstname)
                                append("lastname", customer.lastname)
                                append("email", customer.email)
                                append("subject", "Broken bag")
                                append("message", "My checked bag was damaged.")
                            },
                        ),
                    )
                }

            withClue("help form submission redirects after creating a ticket") {
                assertEquals(HttpStatusCode.Found, response.status)
                assertEquals("/help?ticket=created#contact", response.headers[HttpHeaders.Location])
            }
            withClue("ticket is stored as a requested user ticket") {
                val tickets = TicketRepository.searchFullBySource("USER", "Broken bag", "")
                assertEquals(1, tickets.size)
                assertEquals(customer.id, tickets.single().ticket.userID)
            }

            admin.login("ticket-admin@example.com")
            withClue("staff requested tickets page displays the help ticket") {
                val ticket = TicketRepository.searchFullBySource("USER", "Broken bag", "").single().ticket
                val staffTickets = admin.get("/staff/tickets?tab=requested").bodyAsText()
                assertTrue(staffTickets.contains("Ticket No. ${ticket.ticketID}"))
                assertTrue(staffTickets.contains("${customer.firstname} ${customer.lastname}"))
            }
        }

    @Test
    fun `seat and payment endpoints fail safely before external services`() =
        testApplication {
            configureExtraRoutes()
            val authed = createClient { install(HttpCookies) }
            createUser("seat@example.com", roleId = CUSTOMER_ROLE_ID)
            authed.login("seat@example.com")

            withClue("seat hold requires login") {
                assertEquals(HttpStatusCode.Unauthorized, client.post("/book/seats/hold").status)
            }
            withClue("invalid authed seat hold gives bad request") {
                assertEquals(HttpStatusCode.BadRequest, authed.submitForm("/book/seats/hold", Parameters.Empty).status)
            }
            withClue("payment setup intent requires login") {
                assertEquals(HttpStatusCode.Unauthorized, client.post("/book/payment/setup-intent").status)
            }
            withClue("payment confirm rejects missing or unsucceeded setup intent") {
                val response = authed.submitForm("/book/payment/confirm", Parameters.Empty)
                assertEquals(HttpStatusCode.PaymentRequired, response.status)
            }
        }

    private fun createUser(
        email: String,
        roleId: Int,
        password: String = "password",
    ): data.User =
        UserRepository.add(
            firstname = "Test",
            lastname = "User",
            roleId = roleId,
            email = email,
            password = BCrypt.hashpw(password, BCrypt.gensalt()),
        )
}

private suspend fun io.ktor.client.HttpClient.loginWithRedirect(redirect: String) =
    submitForm(
        url = "/login",
        formParameters =
            Parameters.build {
                append("email", "safe@example.com")
                append("password", "password")
                append("redirect", redirect)
            },
    )

private fun io.ktor.client.statement.HttpResponse.redirectTarget(): String? =
    headers[HttpHeaders.Location] ?: headers["HX-Redirect"]

private suspend fun io.ktor.client.HttpClient.login(email: String) {
    submitForm(
        url = "/login",
        formParameters =
            Parameters.build {
                append("email", email)
                append("password", "password")
            },
    )
}

private fun io.ktor.server.testing.ApplicationTestBuilder.configureExtraRoutes() {
    application {
        install(Sessions) {
            cookie<SessionUtils>("SESSION")
            cookie<UserSession>("USER_SESSION")
        }
        routing {
            signUpRoutes()
            logInRoutes()
            chatRoutes()
            helpRoutes()
            flightsRoutes()
            staffRoutes()
        }
    }
}

private const val CUSTOMER_ROLE_ID = 0
private const val STAFF_ADMIN_ROLE_ID = 2
