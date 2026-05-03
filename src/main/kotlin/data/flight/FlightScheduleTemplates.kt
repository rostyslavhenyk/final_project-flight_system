package data.flight

import data.Routes
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll

object FlightScheduleTemplates : Table("flight_schedule_templates") {
    private const val DAYS_LENGTH = 32
    private const val STATUS_LENGTH = 32

    val id = integer("id").autoIncrement()
    val routeID = integer("routeID").references(Routes.id)
    val departureHour = integer("departureHour")
    val departureMinute = integer("departureMinute")
    val durationMinutes = integer("durationMinutes")
    val basePrice = double("basePrice")
    val daysOfWeek = varchar("daysOfWeek", DAYS_LENGTH)
    val status = varchar("status", STATUS_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class FlightScheduleTemplate(
    val id: Int,
    val routeID: Int,
    val departureHour: Int,
    val departureMinute: Int,
    val durationMinutes: Int,
    val basePrice: Double,
    val daysOfWeek: String,
    val status: String,
)

object FlightScheduleTemplateRepository {
    internal fun ResultRow.toFlightScheduleTemplate() =
        FlightScheduleTemplate(
            id = this[FlightScheduleTemplates.id],
            routeID = this[FlightScheduleTemplates.routeID],
            departureHour = this[FlightScheduleTemplates.departureHour],
            departureMinute = this[FlightScheduleTemplates.departureMinute],
            durationMinutes = this[FlightScheduleTemplates.durationMinutes],
            basePrice = this[FlightScheduleTemplates.basePrice],
            daysOfWeek = this[FlightScheduleTemplates.daysOfWeek],
            status = this[FlightScheduleTemplates.status],
        )

    fun all(): List<FlightScheduleTemplate> =
        FlightScheduleTemplates
            .selectAll()
            .map { it.toFlightScheduleTemplate() }
}
