package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class User(
    val id: Int,
    var firstname: String,
    var lastname: String,
    var role_id: Int,
    var email: String,
    var password: String,
)

object UserRepository {
    private val file = File("data/users.csv")
    private val users = mutableListOf<User>()
    private val idCounter = AtomicInteger(1)
    private val csvHeader = "id,firstname,lastname,role_id,email,password\n"

    public val size: Int
        get() = this.users.size

    public val nullUser: User
        get() = User(-1, "", "", -1, "", "")

    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText(csvHeader)
        } else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 6)
                if (parts.size == 6) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    users.add(User(id, parts[1], parts[2], parts[3].toInt(), parts[4], parts[5]))
                    idCounter.set(maxOf(idCounter.get(), id + 1))
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
        file.writeText(
            csvHeader +
                users.joinToString("\n") {
                    "${it.id},${it.firstname.replace(
                        ",",
                        "",
                    )},${it.lastname.replace(
                        ",",
                        "",
                    )},${it.role_id},${it.email.replace(",", "")},${it.password.replace(",", "")}"
                },
        )
    }
}
