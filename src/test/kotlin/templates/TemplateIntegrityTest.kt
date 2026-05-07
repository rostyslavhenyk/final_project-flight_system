package templates

import routes.pebbleEngine
import testsupport.withClue
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateIntegrityTest {
    @Test
    fun `all template extends and includes point to existing peb files`() {
        val missingReferences =
            templateFiles().flatMap { template ->
                templateReferences(template)
                    .filterNot { reference -> templatePath(reference).exists() }
                    .map { reference -> "${template.relativeTemplatePath()} -> $reference" }
            }

        withClue("Every Pebble extends/include path should exist under resources/templates") {
            assertEquals(emptyList(), missingReferences)
        }
    }

    @Test
    fun `all local static assets referenced from templates exist`() {
        val missingAssets =
            templateFiles().flatMap { template ->
                staticReferences(template)
                    .filterNot { reference -> staticPath(reference).exists() }
                    .map { reference -> "${template.relativeTemplatePath()} -> $reference" }
            }

        withClue("Every /static reference in templates should point to an existing static resource") {
            assertEquals(emptyList(), missingAssets)
        }
    }

    @Test
    fun `auth templates keep mandatory fields and matching htmx status targets`() {
        val login = templateText("user/log-in/index.peb")
        val signup = templateText("user/sign-up/index.peb")
        val forgot = templateText("user/forgot-password/index.peb")

        withClue("Login form keeps required email and password fields") {
            assertRequiredInput(login, "email")
            assertRequiredInput(login, "password")
        }
        withClue("Login htmx target has a matching status container") {
            assertHtmxTargetExists(login, "log-in-status")
        }
        withClue("Signup form keeps required identity and password fields") {
            listOf("firstname", "lastname", "email", "password").forEach { field ->
                assertRequiredInput(signup, field)
            }
        }
        withClue("Signup htmx target has a matching status container") {
            assertHtmxTargetExists(signup, "sign-up-status")
        }
        withClue("Forgot password page keeps verification status container and forms") {
            assertTrue(forgot.contains("""id="verify-status""""))
            listOf("reset-email", "reset-phone", "reset-code", "new-password", "confirm-password").forEach { id ->
                assertTrue(forgot.contains("""id="$id""""))
            }
            assertTrue(forgot.contains("/static/js/forgot-password.js"))
        }
    }

    @Test
    fun `booking templates keep critical client hooks`() {
        val passengers = templateText("user/flights/step-2-passengers/index.peb")
        val seats = templateText("user/flights/step-3-seats/index.peb")
        val payment = templateText("user/flights/step-4-payment/index.peb")
        val seatScript = Files.readString(staticPath("/static/js/book-seats.js"))

        withClue("Passenger page keeps passenger form and phone fields") {
            assertTrue(passengers.contains("data-bp-passenger-form"))
            assertTrue(passengers.contains("""name="contactPhone""""))
            assertTrue(passengers.contains("""name="membershipNumber""""))
        }
        withClue("Seat page keeps seat data and unavailable-seat endpoints") {
            assertTrue(seats.contains("""id="seat-booking-config""""))
            assertTrue(seats.contains("data-journeys-b64"))
            assertTrue(seats.contains("/static/js/book-seats.js"))
            assertTrue(seatScript.contains("/book/seats/unavailable"))
            assertTrue(seatScript.contains("/book/seats/validate"))
        }
        withClue("Payment page keeps Stripe element and pay button hooks") {
            assertTrue(payment.contains("""id="stripe-card-element""""))
            assertTrue(payment.contains("""id="pay-now-button""""))
            assertTrue(payment.contains("/static/js/book-payment.js"))
        }
    }

    @Test
    fun `representative templates render without pebble errors`() {
        val smokeTemplates =
            mapOf(
                "user/log-in/index.peb" to mapOf("title" to "Log In", "redirect" to "/"),
                "user/sign-up/index.peb" to mapOf("title" to "Sign Up"),
                "user/forgot-password/index.peb" to mapOf("title" to "Forgot Password"),
                "user/help/index.peb" to helpModel(),
                "staff/chat/index.peb" to mapOf("title" to "Staff Chat", "conversations" to emptyList<Any>()),
            )

        smokeTemplates.forEach { (templatePath, model) ->
            withClue("$templatePath should render without throwing") {
                val output = renderTemplate(templatePath, model + baseModel())
                assertTrue(output.contains("<html"))
                assertTrue(output.contains("</html>"))
            }
        }
    }

    @Test
    fun `booking payment template renders with realistic populated model`() {
        val output =
            renderTemplate(
                "user/flights/step-4-payment/index.peb",
                paymentModel() + baseModel(loggedIn = true),
            )

        withClue("payment template renders passenger, fee, extra and total sections") {
            assertTrue(output.contains("Ada Lovelace"))
            assertTrue(output.contains("Seat fees subtotal"))
            assertTrue(output.contains("Checked bag"))
            assertTrue(output.contains("GBP 255.00"))
            assertTrue(output.contains("""id="stripe-card-element""""))
        }
    }

    @Test
    fun `staff ticket detail template renders populated model`() {
        val output = renderTemplate("staff/tickets/detail.peb", staffTicketModel() + baseModel(isStaff = true))

        withClue("staff ticket detail renders customer, status form and image links") {
            assertTrue(output.contains("Damaged baggage"))
            assertTrue(output.contains("Ada Lovelace - ada@example.com"))
            assertTrue(output.contains("""name="status""""))
            assertTrue(output.contains("/staff/tickets/images/7"))
        }
    }

    @Test
    fun `every template can be loaded by the pebble engine`() {
        val loadFailures =
            templateFiles().mapNotNull { template ->
                val relative = template.relativeTemplatePath()
                runCatching { pebbleEngine.getTemplate(relative) }
                    .exceptionOrNull()
                    ?.let { error -> "$relative: ${error.message}" }
            }

        withClue("Pebble should be able to load every template file") {
            assertEquals(emptyList(), loadFailures)
        }
    }
}

private val root: Path = Paths.get("").toAbsolutePath()
private val templatesRoot: Path = root.resolve("src/main/resources/templates")
private val staticRoot: Path = root.resolve("src/main/resources/static")

private val pebbleReferenceRegex = Regex("""\{%\s*(?:extends|include)\s+['"]([^'"]+)['"]""")
private val staticReferenceRegex = Regex("""["'](/static/[^"'?#\s]+)""")

private fun templateFiles(): List<Path> =
    Files
        .walk(templatesRoot)
        .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".peb") }
        .toList()

private fun templateReferences(template: Path): List<String> =
    pebbleReferenceRegex.findAll(Files.readString(template)).map { match -> match.groupValues[1] }.toList()

private fun staticReferences(template: Path): List<String> =
    staticReferenceRegex
        .findAll(Files.readString(template))
        .map { match -> match.groupValues[1] }
        .distinct()
        .toList()

private fun templatePath(reference: String): Path = templatesRoot.resolve(reference)

private fun staticPath(reference: String): Path = staticRoot.resolve(reference.removePrefix("/static/"))

private fun Path.relativeTemplatePath(): String = relativeTo(templatesRoot).toString().replace("\\", "/")

private fun templateText(relativePath: String): String = Files.readString(templatePath(relativePath))

private fun assertRequiredInput(
    template: String,
    name: String,
) {
    val inputRegex = Regex("""<input\b[^>]*\bname="$name"[^>]*>""")
    val input = inputRegex.find(template)?.value.orEmpty()
    assertTrue(input.contains("required"), "Expected $name input to be required")
    assertTrue(input.contains("""aria-required="true""""), "Expected $name input to expose aria-required")
}

private fun assertHtmxTargetExists(
    template: String,
    statusId: String,
) {
    assertTrue(template.contains("""hx-target="#$statusId""""))
    assertTrue(template.contains("""id="$statusId""""))
}

private fun renderTemplate(
    templatePath: String,
    model: Map<String, Any?>,
): String {
    val writer = StringWriter()
    pebbleEngine.getTemplate(templatePath).evaluate(writer, model)
    return writer.toString()
}

private fun baseModel(
    loggedIn: Boolean = false,
    isStaff: Boolean = false,
): Map<String, Any?> =
    mapOf(
        "loggedIn" to loggedIn,
        "user" to mapOf("id" to 1, "firstname" to "Ada"),
        "isStaff" to isStaff,
        "isStaffAdmin" to false,
        "sessionId" to "test",
    )

private fun helpModel(): Map<String, Any?> =
    mapOf(
        "title" to "Help",
        "ticketCreated" to false,
        "ticketError" to null,
        "ticketForm" to emptyMap<String, String>(),
    )

private fun paymentModel(): Map<String, Any?> =
    mapOf(
        "chooseFlightsHref" to "/search-flights",
        "passengersHref" to "/book/passengers",
        "seatsHref" to "/book/seats",
        "dualLeg" to false,
        "isReturn" to false,
        "departingCard" to null,
        "returningCard" to null,
        "departingRouteLine" to "Manchester to New York",
        "returningRouteLine" to "",
        "departingDateDisplay" to "7 May 2026",
        "returningDateDisplay" to "",
        "outboundPackageName" to "Economy Flex",
        "inboundPackageName" to "",
        "hasDepartingCard" to false,
        "hasReturningCard" to false,
        "outboundPerPersonPlain" to "200.00",
        "inboundPerPersonPlain" to "0.00",
        "step1BreakdownRows" to listOf(mapOf("label" to "Fare - Economy Flex", "amountPlain" to "200.00")),
        "step1FlightsPerPersonSubtotalPlain" to "200.00",
        "passengerRows" to
            listOf(
                mapOf("slot" to 1, "displayName" to "Ada Lovelace", "outboundSeats" to "12A", "returnSeats" to ""),
            ),
        "feeRows" to listOf(mapOf("journeyLabel" to "Selected journey", "feeGbp" to 30)),
        "totalSeatFeeGbp" to 30,
        "seatFeesSubtotalPlain" to "30.00",
        "hasSeatSelection" to true,
        "hasBookingExtras" to true,
        "bookingExtraRows" to listOf(mapOf("label" to "Checked bag", "amountPlain" to "25.00")),
        "totalBookingExtrasFeeGbp" to 25,
        "bookingExtrasSubtotalPlain" to "25.00",
        "totalStep2ExtrasGbp" to 55,
        "step2ExtrasSubtotalPlain" to "55.00",
        "paxCount" to 1,
        "perPassengerCombinedPlain" to "255.00",
        "grandTotalPlain" to "255.00",
    )

private fun staffTicketModel(): Map<String, Any?> =
    mapOf(
        "updated" to false,
        "ticketFull" to
            mapOf(
                "ticket" to
                    mapOf(
                        "ticketID" to 42,
                        "source" to "USER",
                        "subject" to "Damaged baggage",
                        "message" to "My bag arrived damaged.",
                        "status" to "OPEN",
                        "priority" to "NORMAL",
                    ),
                "user" to
                    mapOf(
                        "id" to 1,
                        "firstname" to "Ada",
                        "lastname" to "Lovelace",
                        "email" to "ada@example.com",
                    ),
                "createdAtText" to "7 May 2026",
                "updatedAtText" to "7 May 2026",
            ),
        "ticketImages" to listOf(mapOf("id" to 7, "filename" to "bag.png")),
    )
