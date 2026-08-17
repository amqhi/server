package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

data class Album(
    override val id: UUID,
    override val userId: UUID,
    override val type: ItemType = ItemType.ALBUM,
    override val name: String?,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val deletedAt: OffsetDateTime?,
    override val eventAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    override val comment: String?,
    override val appScope: Int,
    val metadata: JsonObject
) : ItemCore, ItemAttributesCore {
    override fun toSummaryJson(): JsonObject {
        TODO("Not yet implemented")
    }

    override fun toJson(): JsonObject {
        TODO("Not yet implemented")
    }

    companion object {
        fun from(row: Row): Album = Album(
            id = row.getUUID("id"),
            userId = row.getUUID("user_id"),
            name = row.getStringOrNull("name"),
            createdAt = row.getOffsetDateTime("created_at"),
            updatedAt = row.getOffsetDateTime("updated_at"),
            deletedAt = row.getValue("deleted_at") as OffsetDateTime?,
            eventAt = row.getValue("event_at") as OffsetDateTime?,
            parentId = row.getUUID("parent_id"),
            encrypted = row.getBoolean("encrypted") ?: false,
            comment = row.getValue("comment") as? String,
            appScope = row.getInteger("app_scope"),
            metadata = row.getJsonObject("metadata")
        )
    }
}
