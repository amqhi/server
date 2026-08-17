package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

data class FileItem(
    override val id: UUID,
    override val userId: UUID,
    override val type: ItemType = ItemType.FILE,
    override val name: String?,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val deletedAt: OffsetDateTime?,
    override val eventAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    override val comment: String?,
    override val appScope: Int,
    val checksum: String,
    val size: Long,
    val mimeType: String
) : ItemCore, ItemAttributesCore {
    override fun toSummaryJson(): JsonObject {
        return toJson()
    }

    override fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id.toString())
            .put("type", type.toString().lowercase())
            .put("name", name)
            .put("created_at", createdAt.toString())
            .put("updated_at", updatedAt.toString())
            .put("deleted_at", deletedAt?.toString())
            .put("event_at", eventAt?.toString())
            .put("parent_id", parentId?.toString())
            .put("encrypted", encrypted)
            .put("comment", comment)
            .put("app_scope", appScopeToJsonArray(appScope))
            .put("checksum", checksum)
            .put("size", size)
            .put("mime_type", mimeType)
    }

    companion object {
        fun from(row: Row): FileItem {
            return FileItem(
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
                checksum = row.getValue("checksum") as String,
                size = row.getValue("size") as Long,
                mimeType = row.getString("mime_type")
            )
        }
    }
}