package com.amqhi.services

import com.amqhi.models.LoginType
import com.amqhi.models.User
import com.amqhi.utils.getStringOrNull
import com.password4j.Password
import io.vertx.core.Future
import io.vertx.core.WorkerExecutor
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class UsersService(private val pool: Pool, private val workerExecutor: WorkerExecutor) {

    fun createdUser(email: String, name: String, rawPassword: String) : Future<User> {
        return workerExecutor.executeBlocking {
            Password.hash(rawPassword).withArgon2().result
        }.compose { password ->
            pool.preparedQuery("""
             INSERT INTO "users" (email, name, login_type, password)
        VALUES ($1, $2, $3, $4)
        RETURNING id
        """.trimIndent())
                .execute(Tuple.of(email, name, LoginType.NORMAL.toString().lowercase(), password))
                .map { rows ->
                    User(
                        id = rows.first().getUUID("id"),
                        email = email,
                        name = name,
                        loginType = LoginType.NORMAL
                    )
                }
        }
    }

    fun getUserById(id: UUID) : Future<User> {
        return pool.preparedQuery("""
            SELECT id, email, name, login_type, created_at, updated_at, profile_picture_id FROM "users" WHERE id = $1
        """.trimIndent())
            .execute(Tuple.of(id))
            .map { rows ->
                val row = rows.first()
                User(
                    id = row.getUUID("id"),
                    name = row.getString("name"),
                    email = row.getString("email"),
                    loginType = row.getStringOrNull("login_type")?.let { LoginType.valueOf(it.uppercase()) },
                    createdAt =  row.getOffsetDateTime("created_at"),
                    updatedAt = row.getOffsetDateTime("updated_at"),
                    profilePictureId = row.getValue("profile_picture_id") as? UUID
                )
            }
    }
}