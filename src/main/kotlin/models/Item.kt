package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

enum class ItemType { NOTE, FILE, FOLDER, SONG, ARTIST, ALBUM, ALIAS, UNKNOWN }

interface ItemCore {
    val id: UUID
    val type: ItemType
    val createdAt: OffsetDateTime
    val updatedAt: OffsetDateTime
    val deletedAt: OffsetDateTime?

    fun toSummaryJson(): JsonObject
    fun toJson(): JsonObject
}

interface ItemAttributesCore {
    val userId: UUID
    val name: String?
    val eventAt: OffsetDateTime?
    val parentId: UUID?
    val encrypted: Boolean
    val comment: String?
    val appScope: Int
}

data class ItemAttributes(
    override val userId: UUID,
    override val name: String?,
    override val eventAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    override val comment: String?,

    /**
     * Bitmask representing which applications can view/access this item.
     * Default value is 63 (0b111111: all scopes enabled).
     */
    override val appScope: Int
) : ItemAttributesCore {
    companion object {
        fun from(userId: UUID, jsonObject: JsonObject) : ItemAttributes {
            return ItemAttributes(
                userId = userId,
                name = jsonObject.getValue("name") as? String,
                eventAt = jsonObject.getValue("event_at") as? OffsetDateTime,
                parentId = (jsonObject.getValue("parent_id") as? String)?.let { UUID.fromString(it) },
                encrypted = jsonObject.getValue("encrypted") as? Boolean ?: false,
                comment = jsonObject.getValue("comment") as? String,
                appScope = (jsonObject.getValue("app_scope") as? JsonArray)?.let {
                    appScopeFromJsonArray(it)
                } ?: 63
            )
        }

        fun from(userId: UUID, context: RoutingContext) : ItemAttributes {
            val json = context.body().asJsonObject()
            return from(userId,json)
        }
    }
}

fun appScopeFromJsonArray(jsonArray: JsonArray): Int {
    val appScope = jsonArray.fold(0) { acc, scope ->
        acc or when (scope) {
            "cloud"  -> 1 shl 0 // 1
            "notes"  -> 1 shl 1 // 2
            "music"  -> 1 shl 2 // 4
            "photos" -> 1 shl 3 // 8
            "web"    -> 1 shl 4 // 16
            "ai"     -> 1 shl 5 // 32
            else     -> 0
        }
    }

    return appScope
}

fun appScopeToJsonArray(appScope: Int): JsonArray {
    val jsonArray = JsonArray()

    if ((appScope and 1) != 0) {
        jsonArray.add("cloud")
    }
    if ((appScope and 2) != 0) {
        jsonArray.add("notes")
    }
    if ((appScope and 4) != 0) {
        jsonArray.add("music")
    }
    if ((appScope and 8) != 0) {
        jsonArray.add("photos")
    }
    if ((appScope and 16) != 0) {
        jsonArray.add("web")
    }
    if ((appScope and 32) != 0) {
        jsonArray.add("ai")
    }

    return jsonArray
}

data class Item(
    override val id: UUID,
    override val userId: UUID,
    override val type: ItemType,
    override val name: String?,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val deletedAt: OffsetDateTime?,
    override val eventAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    override val comment: String?,
    override val appScope: Int = 63,
) : ItemCore, ItemAttributesCore {
    override fun toSummaryJson(): JsonObject {
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
    }

    override fun toJson(): JsonObject {
        return toSummaryJson()
    }

    companion object {
        fun from(row: Row): Item = Item(
            id = row.getUUID("id"),
            userId = row.getUUID("user_id"),
            type = ItemType.valueOf(row.getString("type").uppercase()),
            name = row.getStringOrNull("name"),
            createdAt = row.getOffsetDateTime("created_at"),
            updatedAt = row.getOffsetDateTime("updated_at"),
            deletedAt = row.getValue("deleted_at") as OffsetDateTime?,
            eventAt = row.getValue("event_at") as OffsetDateTime?,
            parentId = row.getUUID("parent_id"),
            encrypted = row.getBoolean("encrypted") ?: false,
            comment = row.getValue("comment") as? String,
            appScope = row.getInteger("app_scope"),
        )
    }
}