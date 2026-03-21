package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class Seat(
    val seatID: Int,
    val flightID: Int, //FK
    val number: String,
    val type: String,
    val status: String
)