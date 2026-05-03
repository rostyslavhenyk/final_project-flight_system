package data.flight

internal object FlightSeedData {
    fun countries(): List<SeedCountry> =
        listOf(
            SeedCountry("United Kingdom", "+00:00"),
            SeedCountry("Canada", "-08:00"),
            SeedCountry("France", "+01:00"),
            SeedCountry("Spain", "+01:00"),
            SeedCountry("Italy", "+01:00"),
            SeedCountry("Netherlands", "+01:00"),
            SeedCountry("Germany", "+01:00"),
            SeedCountry("Switzerland", "+01:00"),
            SeedCountry("Denmark", "+01:00"),
            SeedCountry("Ireland", "+00:00"),
            SeedCountry("Portugal", "+00:00"),
            SeedCountry("United Arab Emirates", "+04:00"),
            SeedCountry("United States", "-05:00"),
            SeedCountry("Hong Kong", "+08:00"),
            SeedCountry("Thailand", "+07:00"),
            SeedCountry("Indonesia", "+08:00"),
            SeedCountry("Singapore", "+08:00"),
            SeedCountry("Japan", "+09:00"),
            SeedCountry("Australia", "+10:00"),
            SeedCountry("South Korea", "+09:00"),
            SeedCountry("Turkey", "+03:00"),
            SeedCountry("Qatar", "+03:00"),
            SeedCountry("Malaysia", "+08:00"),
            SeedCountry("India", "+05:30"),
        )

    fun airports(countryIdsByName: Map<String, Int>): List<SeedAirport> =
        listOf(
            SeedAirport(countryIdsByName.getValue("United Kingdom"), "Manchester", "Manchester Airport", "MAN"),
            SeedAirport(countryIdsByName.getValue("United Kingdom"), "Leeds", "Leeds Bradford Airport", "LBA"),
            SeedAirport(countryIdsByName.getValue("United Kingdom"), "London", "London Heathrow", "LHR"),
            SeedAirport(countryIdsByName.getValue("United Kingdom"), "London", "London Gatwick", "LGW"),
            SeedAirport(countryIdsByName.getValue("United Kingdom"), "Edinburgh", "Edinburgh Airport", "EDI"),
            SeedAirport(countryIdsByName.getValue("France"), "Paris", "Charles de Gaulle Airport", "CDG"),
            SeedAirport(countryIdsByName.getValue("Spain"), "Barcelona", "Barcelona-El Prat Airport", "BCN"),
            SeedAirport(countryIdsByName.getValue("Italy"), "Rome", "Leonardo da Vinci-Fiumicino Airport", "FCO"),
            SeedAirport(countryIdsByName.getValue("Netherlands"), "Amsterdam", "Amsterdam Schiphol Airport", "AMS"),
            SeedAirport(countryIdsByName.getValue("Germany"), "Frankfurt", "Frankfurt Airport", "FRA"),
            SeedAirport(countryIdsByName.getValue("Germany"), "Munich", "Munich Airport", "MUC"),
            SeedAirport(countryIdsByName.getValue("Switzerland"), "Zurich", "Zurich Airport", "ZRH"),
            SeedAirport(countryIdsByName.getValue("Denmark"), "Copenhagen", "Copenhagen Airport", "CPH"),
            SeedAirport(
                countryIdsByName.getValue("United Arab Emirates"),
                "Dubai",
                "Dubai International Airport",
                "DXB",
            ),
            SeedAirport(
                countryIdsByName.getValue("United Arab Emirates"),
                "Abu Dhabi",
                "Zayed International Airport",
                "AUH",
            ),
            SeedAirport(
                countryIdsByName.getValue("United States"),
                "New York",
                "John F. Kennedy International Airport",
                "JFK",
            ),
            SeedAirport(
                countryIdsByName.getValue("United States"),
                "Los Angeles",
                "Los Angeles International Airport",
                "LAX",
            ),
            SeedAirport(countryIdsByName.getValue("Canada"), "Vancouver", "Vancouver International Airport", "YVR"),
            SeedAirport(countryIdsByName.getValue("Hong Kong"), "Hong Kong", "Hong Kong International Airport", "HKG"),
            SeedAirport(countryIdsByName.getValue("Thailand"), "Bangkok", "Suvarnabhumi Airport", "BKK"),
            SeedAirport(countryIdsByName.getValue("Indonesia"), "Bali", "Ngurah Rai International Airport", "DPS"),
            SeedAirport(countryIdsByName.getValue("Singapore"), "Singapore", "Singapore Changi Airport", "SIN"),
            SeedAirport(countryIdsByName.getValue("Japan"), "Tokyo", "Narita International Airport", "NRT"),
            SeedAirport(countryIdsByName.getValue("Australia"), "Sydney", "Sydney Airport", "SYD"),
            SeedAirport(countryIdsByName.getValue("South Korea"), "Seoul", "Incheon International Airport", "ICN"),
            SeedAirport(countryIdsByName.getValue("Turkey"), "Istanbul", "Istanbul Airport", "IST"),
            SeedAirport(countryIdsByName.getValue("Qatar"), "Doha", "Hamad International Airport", "DOH"),
            SeedAirport(
                countryIdsByName.getValue("Malaysia"),
                "Kuala Lumpur",
                "Kuala Lumpur International Airport",
                "KUL",
            ),
            SeedAirport(countryIdsByName.getValue("India"), "Delhi", "Indira Gandhi International Airport", "DEL"),
        )
}

internal data class SeedCountry(
    val name: String,
    val timeZone: String,
)

internal data class SeedAirport(
    val countryId: Int,
    val city: String,
    val name: String,
    val code: String,
)
