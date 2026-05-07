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

    fun airportCoordinates(): Map<String, SeedAirportCoordinate> =
        mapOf(
            "MAN" to SeedAirportCoordinate("53.365", "-2.272"),
            "LBA" to SeedAirportCoordinate("53.865", "-1.661"),
            "LHR" to SeedAirportCoordinate("51.470", "-0.454"),
            "LGW" to SeedAirportCoordinate("51.153", "-0.182"),
            "EDI" to SeedAirportCoordinate("55.950", "-3.372"),
            "CDG" to SeedAirportCoordinate("49.009", "2.548"),
            "BCN" to SeedAirportCoordinate("41.297", "2.083"),
            "FCO" to SeedAirportCoordinate("41.800", "12.238"),
            "AMS" to SeedAirportCoordinate("52.310", "4.768"),
            "FRA" to SeedAirportCoordinate("50.037", "8.562"),
            "MUC" to SeedAirportCoordinate("48.354", "11.786"),
            "ZRH" to SeedAirportCoordinate("47.458", "8.555"),
            "CPH" to SeedAirportCoordinate("55.618", "12.656"),
            "DXB" to SeedAirportCoordinate("25.253", "55.365"),
            "AUH" to SeedAirportCoordinate("24.433", "54.651"),
            "JFK" to SeedAirportCoordinate("40.641", "-73.778"),
            "LAX" to SeedAirportCoordinate("33.942", "-118.408"),
            "YVR" to SeedAirportCoordinate("49.196", "-123.181"),
            "HKG" to SeedAirportCoordinate("22.308", "113.918"),
            "BKK" to SeedAirportCoordinate("13.690", "100.750"),
            "DPS" to SeedAirportCoordinate("-8.748", "115.167"),
            "SIN" to SeedAirportCoordinate("1.364", "103.991"),
            "NRT" to SeedAirportCoordinate("35.772", "140.393"),
            "SYD" to SeedAirportCoordinate("-33.939", "151.175"),
            "ICN" to SeedAirportCoordinate("37.460", "126.440"),
            "IST" to SeedAirportCoordinate("41.275", "28.751"),
            "DOH" to SeedAirportCoordinate("25.273", "51.608"),
            "KUL" to SeedAirportCoordinate("2.746", "101.710"),
            "DEL" to SeedAirportCoordinate("28.556", "77.100"),
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

internal data class SeedAirportCoordinate(
    private val latitudeRaw: String,
    private val longitudeRaw: String,
) {
    val latitude: Double = latitudeRaw.toDouble()
    val longitude: Double = longitudeRaw.toDouble()
}
