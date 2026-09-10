/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.models

import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Row
import java.time.OffsetDateTime
import java.util.UUID

enum class SyncEventType {
    CREATE, UPDATE, MOVE, RESTORE, SOFT_DELETE, DELETE
}

data class SyncEvent(
    val itemId: UUID,
    val itemType: ItemType,
    val type: SyncEventType,
    val occurredAt: OffsetDateTime
) {
    fun toJson(): JsonObject {
        return JsonObject()
            .put("item_id", itemId.toString())
            .put("type", type.toString().lowercase())
            .put("occurred_at", occurredAt.toString())
    }

    companion object {
        fun from(row: Row): SyncEvent = SyncEvent(
            itemId = row.getUUID("item_id"),
            itemType = ItemType.valueOf(row.getString("item_type").uppercase()),
            type = SyncEventType.valueOf(row.getString("type").uppercase()),
            occurredAt = row.getOffsetDateTime("occurred_at")
        )
    }
}

data class SyncEventResponse(
    val event: SyncEvent,
    val item: JsonObject
) {
    fun toJson(): JsonObject {
        return JsonObject()
            .put("type", event.type.toString().lowercase())
            .put("occurred_at", event.occurredAt.toString())
            .put("item_type", event.itemType.toString().lowercase())
            .put("item", item)
    }
}
