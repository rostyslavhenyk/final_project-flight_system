package data

import java.io.File
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

data class User(
    val id: Int,
    var firstname: String,
    var lastname: String,
    var role_id: Int,
    var email: String,
    var password: String,
)

/**
 * Users persisted in SQLite only: `data/db/users.db` (no CSV fallback).
 */
object UserRepository {
    private val dbFile = File("data/db/users.db")
    private val users = mutableListOf<User>()
    private val idCounter = AtomicInteger(1)

    public val size: Int
        get() = this.users.size

    public val nullUser: User
        get() = User(-1, "", "", -1, "", "")

    init {
        dbFile.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.path}").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY,
                        firstname TEXT NOT NULL,
                        lastname TEXT NOT NULL,
                        role_id INTEGER NOT NULL,
                        email TEXT NOT NULL,
                        password TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
            conn.prepareStatement("SELECT id, firstname, lastname, role_id, email, password FROM users ORDER BY id").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        users.add(
                            User(
                                id = id,
                                firstname = rs.getString("firstname").orEmpty(),
                                lastname = rs.getString("lastname").orEmpty(),
                                role_id = rs.getInt("role_id"),
                                email = rs.getString("email").orEmpty(),
                                password = rs.getString("password").orEmpty(),
                            ),
                        )
                        idCounter.set(maxOf(idCounter.get(), id + 1))
                    }
                }
            }
        }
    }

    fun all(): List<User> = users.toList()

    fun add(
        firstname: String,
        lastname: String,
        role_id: Int,
        email: String,
        password: String,
    ): User {
        val user = User(idCounter.getAndIncrement(), firstname, lastname, role_id, email, password)
        users.add(user)
        persist()
        return user
    }

    fun delete(id: Int): Boolean {
        val removed = users.removeIf { it.id == id }
        if (removed) persist()
        return removed
    }

    fun get(id: Int): User {
        for (user in users) {
            if (user.id == id) return user
        }
        return this.nullUser
    }

    fun getByEmail(email: String): User {
        for (user in users) {
            if (user.email == email) return user
        }
        return this.nullUser
    }

    fun persist() {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.path}").use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { it.execute("DELETE FROM users") }
                conn.prepareStatement(
                    "INSERT INTO users (id, firstname, lastname, role_id, email, password) VALUES (?,?,?,?,?,?)",
                ).use { ps ->
                    for (u in users) {
                        ps.setInt(1, u.id)
                        ps.setString(2, u.firstname.replace(",", ""))
                        ps.setString(3, u.lastname.replace(",", ""))
                        ps.setInt(4, u.role_id)
                        ps.setString(5, u.email.replace(",", ""))
                        ps.setString(6, u.password.replace(",", ""))
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }
}
