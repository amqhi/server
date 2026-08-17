package com.amqhi.services

import com.amqhi.models.Item
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple

class FoldersService(private val pool: Pool) {

    fun createFolder(itemAttributes: ItemAttributes) : Future<Item> {
        return pool.preparedQuery("""
            INSERT INTO items(user_id, type, created_at, updated_at, event_at, parent_id, name, comment, encrypted, app_scope) VALUES ($1, $2, NOW(), NOW(), $3, $4, $5, $6, $7, $8)
                 RETURNING *
                 """.trimIndent())
            .execute(Tuple.of(itemAttributes.userId, ItemType.FOLDER.toString().lowercase(), itemAttributes.eventAt,itemAttributes.parentId, itemAttributes.name, itemAttributes.comment, itemAttributes.encrypted, itemAttributes.appScope))
            .map { rows ->
                Item.from(rows.first())
            }
    }

}