package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction

object Purchases : Table("purchases") {
    private const val BOOKING_QUERY_LENGTH = 8192

    val id = integer("id").autoIncrement()

    val userID = integer("userID").references(Users.id)

    val amount = double("amount")

    val createdAt = long("createdAt")

    val bookingQuery = varchar("bookingQuery", BOOKING_QUERY_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

data class Purchase(
    val purchaseID: Int,
    val userID: Int,
    val amount: Double,
    val createdAt: Long,
    val bookingQuery: String?,
)

object PurchaseRepository {
    internal fun ResultRow.toPurchase() =
        Purchase(
            purchaseID = this[Purchases.id],
            userID = this[Purchases.userID],
            amount = this[Purchases.amount],
            createdAt = this[Purchases.createdAt],
            bookingQuery = this[Purchases.bookingQuery],
        )

    fun all(): List<Purchase> =
        transaction {
            Purchases.selectAll().map { it.toPurchase() }
        }

    fun allFull(): List<PurchaseFull> =
        transaction {
            val paymentsByPurchase =
                Payments
                    .selectAll()
                    .map { PaymentRepository.run { it.toPayment() } }
                    .groupBy { it.purchaseID }

            Purchases
                .innerJoin(Users, { Purchases.userID }, { Users.id })
                .selectAll()
                .map {
                    val purchase = it.toPurchase()

                    PurchaseFull(
                        purchase = purchase,
                        user = UserRepository.run { it.toUser() },
                        payments = paymentsByPurchase[purchase.purchaseID].orEmpty(),
                    )
                }
        }

    fun create(
        userID: Int,
        amount: Double,
        createdAt: Long = System.currentTimeMillis(),
        bookingQuery: String? = null,
    ): Purchase =
        transaction {
            val id =
                Purchases.insert {
                    it[Purchases.userID] = userID
                    it[Purchases.amount] = amount
                    it[Purchases.createdAt] = createdAt
                    it[Purchases.bookingQuery] = bookingQuery
                } get Purchases.id

            Purchase(id, userID, amount, createdAt, bookingQuery)
        }

    fun allByUser(userID: Int): List<Purchase> =
        transaction {
            Purchases
                .selectAll()
                .where { Purchases.userID eq userID }
                .map { it.toPurchase() }
        }

    fun get(id: Int): Purchase? =
        transaction {
            Purchases
                .selectAll()
                .where { Purchases.id eq id }
                .map { it.toPurchase() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            Purchases.deleteWhere { Purchases.id eq id } > 0
        }
}

data class PurchaseFull(
    val purchase: Purchase,
    val user: User,
    val payments: List<Payment>,
)
