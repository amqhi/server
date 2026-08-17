package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val name: String,
    val email: String,
    val loginType: LoginType? = null,
    val providerId: String? = null,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
    val profilePictureId: UUID? = null
) {
    companion object {
        fun from(row: Row): User = User(
            id = row.getUUID("id"),
            name = row.getString("name"),
            email = row.getString("email"),
            loginType = row.getStringOrNull("login_type")?.let { LoginType.valueOf(it.uppercase()) },
            providerId = row.getStringOrNull("provider_id"),
            createdAt =  row.getValue("created_at") as? OffsetDateTime,
            updatedAt = row.getValue("updated_at") as? OffsetDateTime,
            profilePictureId = row.getValue("profile_picture_id") as? UUID
        )
    }
}

data class AuthenticatedUser(
    val id: UUID,
    val name: String,
    val email: String,
    val deviceId: UUID,
    val deviceBitMask: Int
)

enum class LoginType {
    NORMAL,
    GOOGLE,
    APPLE,
    MICROSOFT,
//    PROTON("proton"),
}