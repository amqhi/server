package com.amqhi.models

import io.vertx.core.json.JsonObject
import java.time.OffsetDateTime
import java.util.UUID

data class TokenPair(
    val accessToken: AccessToken,
    val refreshToken: AuthToken
) {
    fun toResponse() : String {
        return JsonObject()
            .put("access_token", accessToken.value)
            .put("refresh_token", refreshToken.value)
            .put("token_type", "bearer")
            .put("expires_in", accessToken.expiresIn)
            .toString()
    }
}

data class AuthToken(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val value: String,
    val expiresAt: OffsetDateTime,
    val ipAddress: String?
)

data class AccessToken(
    val value: String,
    val expiresIn: Long
)