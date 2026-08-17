package com.amqhi.models

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

enum class SyncEventType {
    CREATE, UPDATE, MOVE, RESTORE, SOFT_DELETE, DELETE
}

data class SyncEvent(
    val id: UUID,
    val itemId: UUID,
    val type: SyncEventType,
    val occurredAt: OffsetDateTime
) {
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id.toString())
            .put("item_id", itemId.toString())
            .put("type", type.toString().lowercase())
            .put("occurred_at", occurredAt.toString())
    }

    companion object {
        fun from(row: Row): SyncEvent = SyncEvent(
            id = row.getUUID("id"),
            itemId = row.getUUID("item_id"),
            type = SyncEventType.valueOf(row.getString("type").uppercase()),
            occurredAt = row.getOffsetDateTime("occurred_at")
        )
    }
}
