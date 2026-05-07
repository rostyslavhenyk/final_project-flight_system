package data.flight

import data.AirportRepository
import data.Bookings
import data.Airports
import data.Countries
import data.FlightRepository
import data.Flights
import data.RouteRepository
import data.Routes
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

private const val ACTIVE_STATUS = "scheduled"

object FlightScheduleGenerator {
    private const val DAYS_TO_GENERATE = 28

    fun ensureSeedData() {
        ensureCountryTimezoneColumn()
        ensureFlightSearchIndexes()
        seedAirportsRoutesAndTemplates()
        deleteExpiredUnbookedFlights()
        generateMissingFlights()
    }

    private fun ensureCountryTimezoneColumn() {
        runCatching {
            TransactionManager.current().exec(
                "ALTER TABLE countries ADD COLUMN timeZone VARCHAR(8) NOT NULL DEFAULT '+00:00'",
            )
        }
    }

    private fun ensureFlightSearchIndexes() {
        TransactionManager.current().exec(
            "CREATE INDEX IF NOT EXISTS idx_flights_departure_time ON flights(departureTime)",
        )
        TransactionManager.current().exec(
            "CREATE INDEX IF NOT EXISTS idx_flights_route_departure_time ON flights(routeID, departureTime)",
        )
    }

    private fun seedAirportsRoutesAndTemplates() {
        FlightSeedData.countries().forEach { country ->
            FlightScheduleSeedMaintenance.ensureCountry(country.name, country.timeZone)
        }

        val countriesByName =
            Countries
                .selectAll()
                .associate { countryRow -> countryRow[Countries.name] to countryRow[Countries.id] }

        val seedAirports = FlightSeedData.airports(countriesByName)
        seedAirports.forEach { airport ->
            FlightScheduleSeedMaintenance.ensureAirport(airport)
        }

        val airportCodes = seedAirports.map { airport -> airport.code }
        val routePairs =
            airportCodes.flatMap { departureCode ->
                airportCodes
                    .filterNot { arrivalCode -> arrivalCode == departureCode }
                    .map { arrivalCode -> departureCode to arrivalCode }
            }

        routePairs.forEach { routePair ->
            FlightScheduleSeedMaintenance.ensureRoute(routePair.first, routePair.second)
        }

        RouteRepository.allFull().forEachIndexed { routeIndex, routeFull ->
            val durationMinutes =
                FlightScheduleRules.durationForRoute(
                    routeFull.departureAirport.code,
                    routeFull.arrivalAirport.code,
                    routeIndex,
                )
            val basePrice =
                FlightScheduleRules.priceForRoute(
                    routeFull.departureAirport.code,
                    routeFull.arrivalAirport.code,
                    durationMinutes,
                )
            val departureTimes = FlightScheduleRules.weeklyDepartureTimes(routeIndex)
            departureTimes.forEach { departureTime ->
                FlightScheduleSeedMaintenance.ensureTemplate(
                    routeId = routeFull.route.routeID,
                    departureTime = departureTime,
                    durationMinutes = durationMinutes,
                    basePrice = basePrice,
                )
            }
        }
    }

    private fun generateMissingFlights() {
        val today = LocalDate.now()
        val routesById = RouteRepository.allFull().associateBy { route -> route.route.routeID }
        val existingFlights =
            Flights
                .selectAll()
                .associateBy { row -> FlightKey(row[Flights.routeId], row[Flights.departureTime]) }

        FlightScheduleTemplateRepository.all().forEach { template ->
            val activeDays =
                template.daysOfWeek
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
            repeat(DAYS_TO_GENERATE) { dayOffset ->
                val flightDate = today.plusDays(dayOffset.toLong())
                if (flightDate.dayOfWeek.value !in activeDays) return@repeat

                val departureDateTime =
                    LocalDateTime.of(
                        flightDate,
                        LocalTime.of(template.departureHour, template.departureMinute),
                    )
                val route = routesById[template.routeID] ?: return@repeat
                val originZone = AirportTimeZoneResolver.zoneIdForIata(route.departureAirport.code)
                val arrivalZone = AirportTimeZoneResolver.zoneIdForIata(route.arrivalAirport.code)
                val arrivalDateTime =
                    ZonedDateTime
                        .of(departureDateTime, originZone)
                        .plusMinutes(template.durationMinutes.toLong())
                        .withZoneSameInstant(arrivalZone)
                        .toLocalDateTime()

                upsertFlight(
                    existingFlight = existingFlights[FlightKey(template.routeID, departureDateTime.toString())],
                    routeId = template.routeID,
                    departureTime = departureDateTime.toString(),
                    arrivalTime = arrivalDateTime.toString(),
                    price = template.basePrice,
                    status = template.status,
                )
            }
        }
    }

    private fun deleteExpiredUnbookedFlights() {
        val todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN).toString()
        Flights.deleteWhere {
            (Flights.departureTime less todayStart) and
                notExists(
                    Bookings
                        .selectAll()
                        .where { Bookings.flightID eq Flights.id },
                )
        }
    }

    private fun upsertFlight(
        existingFlight: ResultRow?,
        routeId: Int,
        departureTime: String,
        arrivalTime: String,
        price: Double,
        status: String,
    ) {
        if (existingFlight == null) {
            FlightRepository.add(
                routeID = routeId,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                price = price,
                status = status,
            )
        } else if (
            existingFlight[Flights.arrivalTime] != arrivalTime ||
            existingFlight[Flights.price] != price ||
            existingFlight[Flights.status] != status
        ) {
            Flights.update({ Flights.id eq existingFlight[Flights.id] }) {
                it[Flights.arrivalTime] = arrivalTime
                it[Flights.price] = price
                it[Flights.status] = status
            }
        }
    }

    private data class FlightKey(
        val routeId: Int,
        val departureTime: String,
    )
}

private object FlightScheduleSeedMaintenance {
    fun ensureCountry(
        name: String,
        timeZone: String,
    ) {
        val exists = Countries.selectAll().where { Countries.name eq name }.any()
        if (!exists) {
            Countries.insert {
                it[Countries.name] = name
                it[Countries.timeZone] = timeZone
            }
        } else {
            Countries.update({ Countries.name eq name }) {
                it[Countries.timeZone] = timeZone
            }
        }
    }

    fun ensureAirport(airport: SeedAirport) {
        val exists = Airports.selectAll().where { Airports.code eq airport.code }.any()
        if (!exists) {
            AirportRepository.add(airport.countryId, airport.city, airport.name, airport.code)
        }
    }

    fun ensureRoute(
        departureCode: String,
        arrivalCode: String,
    ) {
        val airportsByCode = AirportRepository.all().associateBy { airport -> airport.code }
        val departureAirport = airportsByCode[departureCode] ?: return
        val arrivalAirport = airportsByCode[arrivalCode] ?: return
        val exists =
            Routes
                .selectAll()
                .where {
                    (Routes.departureAirportId eq departureAirport.airportID) and
                        (Routes.arrivalAirportId eq arrivalAirport.airportID)
                }.any()
        if (!exists) {
            RouteRepository.add(departureAirport.airportID, arrivalAirport.airportID)
        }
    }

    fun ensureTemplate(
        routeId: Int,
        departureTime: LocalTime,
        durationMinutes: Int,
        basePrice: Double,
    ) {
        val exists =
            FlightScheduleTemplates
                .selectAll()
                .where {
                    (FlightScheduleTemplates.routeID eq routeId) and
                        (FlightScheduleTemplates.departureHour eq departureTime.hour) and
                        (FlightScheduleTemplates.departureMinute eq departureTime.minute)
                }.any()
        if (!exists) {
            FlightScheduleTemplates.insert {
                it[FlightScheduleTemplates.routeID] = routeId
                it[departureHour] = departureTime.hour
                it[departureMinute] = departureTime.minute
                it[FlightScheduleTemplates.durationMinutes] = durationMinutes
                it[FlightScheduleTemplates.basePrice] = basePrice
                it[daysOfWeek] = "1,2,3,4,5,6,7"
                it[status] = ACTIVE_STATUS
            }
        } else {
            FlightScheduleTemplates.update(
                {
                    (FlightScheduleTemplates.routeID eq routeId) and
                        (FlightScheduleTemplates.departureHour eq departureTime.hour) and
                        (FlightScheduleTemplates.departureMinute eq departureTime.minute)
                },
            ) {
                it[FlightScheduleTemplates.durationMinutes] = durationMinutes
                it[FlightScheduleTemplates.basePrice] = basePrice
                it[status] = ACTIVE_STATUS
            }
        }
    }
}
