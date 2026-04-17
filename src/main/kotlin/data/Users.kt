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
    val firstname = varchar("firstname", NAME_LENGTH)
    val lastname = varchar("lastname", NAME_LENGTH)
    val roleId = integer("roleId")
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

    fun get(id: Int): User =
        transaction {
            Users
                .selectAll()
                .where { Users.id eq id }
                .map { it.toUser() }
                .singleOrNull()
                ?: User(-1, "", "", -1, "", "")
        }

    fun getByEmail(email: String): User =
        transaction {
            Users
                .selectAll()
                .where { Users.email eq email }
                .map { it.toUser() }
                .singleOrNull()
                ?: User(-1, "", "", -1, "", "")
        }

    fun delete(id: Int): Boolean =
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }

    private fun ResultRow.toUser(): User =
        User(
            id = this[Users.id],
            firstname = this[Users.firstname],
            lastname = this[Users.lastname],
            roleId = this[Users.roleId],
            email = this[Users.email],
            password = this[Users.password],
        )
}
