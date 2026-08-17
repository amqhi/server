package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

data class Note(
    override val id: UUID,
    override val userId: UUID,
    override val type: ItemType = ItemType.NOTE,
    override val name: String?,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val deletedAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    val title: String?,
    val subtitle: String?,
    val content: JsonArray?,
    val style: JsonObject?,
    val extra: JsonObject?,
    override val eventAt: OffsetDateTime?,
    override val comment: String?,
    override val appScope: Int,
) : ItemCore, ItemAttributesCore {
    override fun toSummaryJson(): JsonObject {
        TODO("Not yet implemented")
    }

    override fun toJson(): JsonObject {
        TODO("Not yet implemented")
    }

    companion object {
        fun from(row: Row): Note = Note(
            id = row.getUUID("id"),
            userId = row.getUUID("user_id"),
            name = row.getStringOrNull("name"),
            createdAt = row.getOffsetDateTime("created_at"),
            updatedAt = row.getOffsetDateTime("updated_at"),
            eventAt = row.getValue("event_at") as OffsetDateTime?,
            deletedAt = row.getValue("deleted_at") as OffsetDateTime?,
            parentId = row.getUUID("parent_id"),
            encrypted = row.getBoolean("encrypted") ?: false,
            comment = row.getValue("comment") as? String,
            appScope = row.getInteger("app_scope"),
            title = row.getStringOrNull("title"),
            subtitle = row.getStringOrNull("subtitle"),
            content = row.getValue("content") as? JsonArray,
            style = row.getValue("style") as? JsonObject,
            extra = row.getValue("extra") as? JsonObject
        )
    }
}