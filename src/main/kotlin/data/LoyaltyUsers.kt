package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction

object LoyaltyUsers : Table("loyalty_users") {
    private const val TIER_NAME = 32

    val id = integer("id").autoIncrement()
    val userID = integer("userID").references(Users.id).uniqueIndex()
    val points = integer("points")
    val tier = varchar("tier", TIER_NAME)
    val joinDate = long("joinDate")

    override val primaryKey = PrimaryKey(id)
}

data class LoyaltyUser(
    val id: Int,
    val userID: Int,
    val points: Int,
    val tier: String,
    val joinDate: Long,
)

object LoyaltyRepository {
    internal fun ResultRow.toLoyaltyUser() =
        LoyaltyUser(
            id = this[LoyaltyUsers.id],
            userID = this[LoyaltyUsers.userID],
            points = this[LoyaltyUsers.points],
            tier = this[LoyaltyUsers.tier],
            joinDate = this[LoyaltyUsers.joinDate],
        )

    fun all(): List<LoyaltyUser> =
        transaction {
            LoyaltyUsers.selectAll().map { it.toLoyaltyUser() }
        }

    fun allFull(): List<LoyaltyUserFull> =
        transaction {
            LoyaltyUsers
                .innerJoin(Users, { LoyaltyUsers.userID }, { Users.id })
                .selectAll()
                .map {
                    LoyaltyUserFull(
                        loyaltyUser = it.toLoyaltyUser(),
                        user = UserRepository.run { it.toUser() },
                    )
                }
        }

    fun getByUser(userID: Int): LoyaltyUser? =
        transaction {
            LoyaltyUsers
                .selectAll()
                .where { LoyaltyUsers.userID eq userID }
                .map { it.toLoyaltyUser() }
                .singleOrNull()
        }

    fun add(
        userID: Int,
        points: Int = 0,
        tier: String = "SILVER",
        joinDate: Long = System.currentTimeMillis(),
    ): LoyaltyUser =
        transaction {
            val id =
                LoyaltyUsers.insert {
                    it[LoyaltyUsers.userID] = userID
                    it[LoyaltyUsers.points] = points
                    it[LoyaltyUsers.tier] = tier
                    it[LoyaltyUsers.joinDate] = joinDate
                } get LoyaltyUsers.id

            LoyaltyUser(id, userID, points, tier, joinDate)
        }

    fun updatePoints(
        userID: Int,
        newPoints: Int,
    ): Boolean =
        transaction {
            LoyaltyUsers.update({ LoyaltyUsers.userID eq userID }) {
                it[points] = newPoints
            } > 0
        }

    fun delete(userID: Int): Boolean =
        transaction {
            LoyaltyUsers.deleteWhere { LoyaltyUsers.userID eq userID } > 0
        }
}

data class LoyaltyUserFull(
    val loyaltyUser: LoyaltyUser,
    val user: User,
)
