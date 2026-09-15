/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.models

import com.amqhi.utils.getStringOrNull
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

interface FolderAttributesCore {
    val backgroundId: UUID?
    val backgroundColor: Color?
    val iconId: UUID?
    val iconColor: Color?
}

data class FolderAttributes(
    override val backgroundId: UUID?,
    override val backgroundColor: Color?,
    override val iconId: UUID?,
    override val iconColor: Color?

) : FolderAttributesCore {
    companion object {
        fun from(jsonObject: JsonObject) : FolderAttributes {
            return FolderAttributes(
                backgroundId = (jsonObject.getValue("background_id") as? String)?.let { UUID.fromString(it) },
                backgroundColor =  (jsonObject.getValue("background_color") as? String)?.let { Color.fromString(it) },
                iconId = (jsonObject.getValue("icon_id") as? String)?.let { UUID.fromString(it) },
                iconColor = (jsonObject.getValue("icon_color") as? String)?.let { Color.fromString(it) }
            )
        }

        fun from(context: RoutingContext) : FolderAttributes {
            val json = context.body().asJsonObject()
            return from(json)
        }
    }
}

data class Folder(
    override val id: UUID,
    override val userId: UUID,
    override val type: ItemType = ItemType.FOLDER,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val deletedAt: OffsetDateTime?,
    override val name: String?,
    override val eventAt: OffsetDateTime?,
    override val parentId: UUID?,
    override val encrypted: Boolean,
    override val comment: String?,
    override val appScope: Int,
    override val backgroundId: UUID?,
    override val backgroundColor: Color?,
    override val iconId: UUID?,
    override val iconColor: Color?
) : ItemCore, ItemAttributesCore, FolderAttributesCore {

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
            .put("background_id", backgroundId?.toString())
            .put("background_color", backgroundColor?.toString())
            .put("icon_id", iconId?.toString())
            .put("icon_color", iconColor?.toString())
    }

    companion object {
        fun from(row: Row): Folder {
            return Folder(
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
                backgroundId = row.getValue("background_id") as? UUID,
                backgroundColor = (row.getValue("background_color") as? Int)?.let { Color(it) },
                iconId = row.getValue("icon_id") as? UUID,
                iconColor = (row.getValue("icon_color") as? Int)?.let { Color(it) },
            )
        }
    }
}
