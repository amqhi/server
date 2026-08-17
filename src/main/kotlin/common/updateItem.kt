package com.amqhi.common

import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.UUID

fun updateItem(pool: Pool, id: UUID, type: ItemType?, itemAttributes: ItemAttributes): Future<RowSet<Row>> {
    return pool.preparedQuery("""
            UPDATE "items" 
            SET "name" = COALESCE($1, "name"), 
            "type" = COALESCE($2, "type"),
            "updated_at" = NOW(),
            "event_at" = COALESCE($3, "event_at"),
            "parent_id" = COALESCE($4, "parent_id"),
            "encrypted" = COALESCE($5, "encrypted")
            WHERE "id" = $6 AND "user_id" = $7
        """.trimIndent())
        .execute(Tuple.of(itemAttributes.name,type?.toString()?.lowercase(), itemAttributes.eventAt, itemAttributes.parentId, itemAttributes.encrypted, id, itemAttributes.userId))
}