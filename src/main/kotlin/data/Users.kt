package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table("users") {
    private const val NAME_LENGTH = 128
    private const val EMAIL_LENGTH = 256
    private const val PASSWORD_LENGTH = 128

    val id = integer("id").autoIncrement()
    val firstname = varchar("firstName", NAME_LENGTH)
    val lastname = varchar("lastName", NAME_LENGTH)
    val roleId = integer("roleID")
    val email = varchar("email", EMAIL_LENGTH).uniqueIndex()
    val password = varchar("password", PASSWORD_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class User(
    val id: Int,
    val firstname: String,
    val lastname: String,
    val roleId: Int,
    val email: String,
    val password: String,
)

object UserRepository {
    fun all(): List<User> =
        transaction {
            Users.selectAll().map { it.toUser() }
        }

    fun allFull(): List<UserFull> =
        transaction {
            val loyaltyByUser =
                LoyaltyUsers
                    .selectAll()
                    .map { LoyaltyRepository.run { it.toLoyaltyUser() } }
                    .associateBy { it.userID }

            Users
                .selectAll()
                .map {
                    val user = it.toUser()

                    UserFull(
                        user = user,
                        loyaltyUser = loyaltyByUser[user.id],
                    )
                }
        }

    fun add(
        firstname: String,
        lastname: String,
        roleId: Int,
        email: String,
        password: String,
    ): User =
        transaction {
            val id =
                Users.insert {
                    it[Users.firstname] = firstname
                    it[Users.lastname] = lastname
                    it[Users.roleId] = roleId
                    it[Users.email] = email
                    it[Users.password] = password
                } get Users.id

            User(id, firstname, lastname, roleId, email, password)
        }

    fun get(id: Int): User? =
        transaction {
            Users
                .selectAll()
                .where { Users.id eq id }
                .map { it.toUser() }
                .singleOrNull()
        }

    fun getByEmail(email: String): User? =
        transaction {
            Users
                .selectAll()
                .where { Users.email eq email }
                .map { it.toUser() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }

    internal fun ResultRow.toUser(): User =
        User(
            id = this[Users.id],
            firstname = this[Users.firstname],
            lastname = this[Users.lastname],
            roleId = this[Users.roleId],
            email = this[Users.email],
            password = this[Users.password],
        )
}

data class UserFull(
    val user: User,
    val loyaltyUser: LoyaltyUser?,
)
