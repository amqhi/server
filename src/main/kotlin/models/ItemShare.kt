package com.amqhi.models

import java.time.OffsetDateTime
import java.util.UUID

data class ItemShare(
    val id: UUID,
    val itemId: UUID,
    val key: String,
    val expiresAt: OffsetDateTime?,
    val viewCount: Int,
    val maxViews: Int?,
    val enabled: Boolean
)