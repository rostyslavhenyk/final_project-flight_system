package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Purchases : Table("purchases") {
    val id = integer("id").autoIncrement()

    val userID = integer("userID").references(Users.id)

    val amount = double("amount")

    val createdAt = long("createdAt")

    override val primaryKey = PrimaryKey(id)
}

data class Purchase(
    val purchaseID: Int,
    val userID: Int,
    val amount: Double,
    val createdAt: Long,
)

object PurchaseRepository {
    private fun ResultRow.toPurchase() =
        Purchase(
            purchaseID = this[Purchases.id],
            userID = this[Purchases.userID],
            amount = this[Purchases.amount],
            createdAt = this[Purchases.createdAt],
        )

    fun create(
        userID: Int,
        amount: Double,
        createdAt: Long = System.currentTimeMillis(),
    ): Purchase =
        transaction {
            val id =
                Purchases.insert {
                    it[Purchases.userID] = userID
                    it[Purchases.amount] = amount
                    it[Purchases.createdAt] = createdAt
                } get Purchases.id

            Purchase(id, userID, amount, createdAt)
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
