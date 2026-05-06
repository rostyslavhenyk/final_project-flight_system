package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Locale

// users table definition
object Users : Table("users") {
    private const val NAME_LENGTH = 128
    private const val EMAIL_LENGTH = 256
    private const val PASSWORD_LENGTH = 128
    private const val PHONE_LENGTH = 20

    val id = integer("id").autoIncrement()
    val firstname = varchar("firstName", NAME_LENGTH)
    val lastname = varchar("lastName", NAME_LENGTH)
    val roleId = integer("roleID")
    val email = varchar("email", EMAIL_LENGTH).uniqueIndex()
    val password = varchar("password", PASSWORD_LENGTH)
    val phone = varchar("phone", PHONE_LENGTH).default("")

    override val primaryKey = PrimaryKey(id)
}

// user data class
data class User(
    val id: Int,
    val firstname: String,
    val lastname: String,
    val roleId: Int,
    val email: String,
    val password: String,
    val phone: String = "",
)

// handles all database queries for users
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
                    .map { LoyaltyUserRepository.run { it.toLoyaltyUser() } }
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
        phone: String = "",
    ): User =
        transaction {
            val normalizedFirstName = normalizePersonName(firstname)
            val normalizedLastName = normalizePersonName(lastname)
            val normalizedEmail = normalizeEmail(email)
            val id =
                Users.insert {
                    it[Users.firstname] = normalizedFirstName
                    it[Users.lastname] = normalizedLastName
                    it[Users.roleId] = roleId
                    it[Users.email] = normalizedEmail
                    it[Users.password] = password
                    it[Users.phone] = phone
                } get Users.id

            User(id, normalizedFirstName, normalizedLastName, roleId, normalizedEmail, password, phone)
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
                .where { Users.email eq normalizeEmail(email) }
                .map { it.toUser() }
                .singleOrNull()
        }

    fun normalizeStoredNames() {
        transaction {
            Users.selectAll().forEach { row ->
                val normalizedFirstName = normalizePersonName(row[Users.firstname])
                val normalizedLastName = normalizePersonName(row[Users.lastname])
                val normalizedEmail = normalizeEmail(row[Users.email])
                if (
                    normalizedFirstName != row[Users.firstname] ||
                    normalizedLastName != row[Users.lastname] ||
                    normalizedEmail != row[Users.email]
                ) {
                    Users.update({ Users.id eq row[Users.id] }) {
                        it[firstname] = normalizedFirstName
                        it[lastname] = normalizedLastName
                        it[email] = normalizedEmail
                    }
                }
            }
        }
    }

    fun delete(id: Int): Boolean =
        transaction {
            Users.deleteWhere { Users.id eq id } > 0
        }

    fun updatePassword(id: Int, newPassword: String): Boolean =
        transaction {
            Users.update({ Users.id eq id }) {
                it[password] = newPassword
            } > 0
        }

    fun updatePhone(id: Int, newPhone: String): Boolean =
        transaction {
            Users.update({ Users.id eq id }) {
                it[phone] = newPhone
            } > 0
        }

    internal fun ResultRow.toUser(): User =
        User(
            id = this[Users.id],
            firstname = this[Users.firstname],
            lastname = this[Users.lastname],
            roleId = this[Users.roleId],
            email = this[Users.email],
            password = this[Users.password],
            phone = this[Users.phone],
        )
}

private fun normalizeEmail(value: String): String = value.trim().lowercase(Locale.UK)

private fun normalizePersonName(value: String): String =
    value
        .trim()
        .lowercase(Locale.UK)
        .split(Regex("\\s+"))
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part -> part.capitalizeNamePart() }

private fun String.capitalizeNamePart(): String =
    splitKeepingDelimiters(this, setOf('-', '\''))
        .joinToString("") { token ->
            if (token.length == 1 && token.first() in setOf('-', '\'')) {
                token
            } else {
                token.replaceFirstChar { char -> char.uppercase(Locale.UK) }
            }
        }

private fun splitKeepingDelimiters(
    value: String,
    delimiters: Set<Char>,
): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    value.forEach { char ->
        if (char in delimiters) {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
            tokens += char.toString()
        } else {
            current.append(char)
        }
    }
    if (current.isNotEmpty()) {
        tokens += current.toString()
    }
    return tokens
}

data class UserFull(
    val user: User,
    val loyaltyUser: LoyaltyUser?,
)
