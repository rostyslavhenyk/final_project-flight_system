package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Payments : Table("payments") {
    private const val PAYMENT_METHOD_LENGTH = 64
    private const val PAYMENT_STATUS_LENGTH = 24
    private const val TRANSACTION_REF_LENGTH = 128

    val id = integer("id").autoIncrement()

    val purchaseID = integer("purchaseID").references(Purchases.id)

    val amount = double("amount")

    val paymentMethod = varchar("paymentMethod", PAYMENT_METHOD_LENGTH)

    val paymentStatus = varchar("paymentStatus", PAYMENT_STATUS_LENGTH)

    val transactionRef = varchar("transactionRef", TRANSACTION_REF_LENGTH)

    val createdAt = long("createdAt")

    override val primaryKey = PrimaryKey(id)
}

data class Payment(
    val paymentID: Int,
    val purchaseID: Int,
    val amount: Double,
    val paymentMethod: String,
    val paymentStatus: String,
    val transactionRef: String,
    val createdAt: Long,
)

object PaymentRepository {
    private fun ResultRow.toPayment() =
        Payment(
            paymentID = this[Payments.id],
            purchaseID = this[Payments.purchaseID],
            amount = this[Payments.amount],
            paymentMethod = this[Payments.paymentMethod],
            paymentStatus = this[Payments.paymentStatus],
            transactionRef = this[Payments.transactionRef],
            createdAt = this[Payments.createdAt],
        )

    fun create(
        purchaseID: Int,
        amount: Double,
        paymentMethod: String,
        paymentStatus: String,
        transactionRef: String,
        createdAt: Long = System.currentTimeMillis(),
    ): Payment =
        transaction {
            val id =
                Payments.insert {
                    it[Payments.purchaseID] = purchaseID
                    it[Payments.amount] = amount
                    it[Payments.paymentMethod] = paymentMethod
                    it[Payments.paymentStatus] = paymentStatus
                    it[Payments.transactionRef] = transactionRef
                    it[Payments.createdAt] = createdAt
                } get Payments.id

            Payment(id, purchaseID, amount, paymentMethod, paymentStatus, transactionRef, createdAt)
        }

    fun getByPurchase(purchaseID: Int): List<Payment> =
        transaction {
            Payments
                .selectAll()
                .where { Payments.purchaseID eq purchaseID }
                .map { it.toPayment() }
        }

    fun delete(id: Int): Boolean =
        transaction {
            Payments.deleteWhere { Payments.id eq id } > 0
        }
}
