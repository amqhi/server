/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.services

import com.amqhi.common.updateItem
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import com.amqhi.models.Note
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class NotesService(private val pool: Pool) {
    fun createNote(
        userId: UUID,
        itemAttributes: ItemAttributes,
        title: String?,
        subtitle: String?,
        content: JsonArray,
        style: JsonObject?,
        extra: JsonObject?
    ): Future<Note> {
        return pool.preparedQuery(
            """
               WITH inserted_item AS (
               INSERT INTO items(user_id, type, created_at, updated_at, event_at, parent_id, name, comment, encrypted, app_scope) VALUES ($1, $2, NOW(), NOW(), $3, $4, $5, $6, $7, $8, $9)
                 RETURNING id
                 INSERT INTO "notes" (
                    "id",
                    "title",
                    "subtitle",
                    "content"
                    "style"
                    "extra"
                 )
                 SELECT 
                 "id", 
                 $11,
                 $12,
                 $13
             FROM inserted_item
             RETURNING *
        """.trimIndent()
        )
            .execute(
                Tuple.of(
                    userId,
                    ItemType.FILE.toString(),
                    itemAttributes.eventAt,
                    itemAttributes.parentId,
                    itemAttributes.name,
                    itemAttributes.comment,
                    itemAttributes.encrypted,
                    itemAttributes.appScope,
                    title,
                    subtitle,
                    content,
                    style,
                    extra
                )
            ).map {
                Note.from(it.first())
            }
    }

    fun updateNote(
        id: UUID,
        userId: UUID,
        itemAttributes: ItemAttributes,
        title: String?,
        subtitle: String?,
        content: JsonArray?,
        style: JsonObject?,
        extra: JsonObject?
    ): Future<Void> {
        return updateItem(pool, id, null, userId, itemAttributes).compose {
            pool.preparedQuery("""
            UPDATE "notes" 
            SET "title" = COALESCE($1, "title"), 
            "subtitle" = COALESCE($2, "subtitle"),
            "content" = COALESCE($3, "content"),
            "style" = COALESCE($4, "style"),
            "extra" = COALESCE($5, "extra"),
            WHERE "id" = $6
        """.trimIndent())
                .execute(
                    Tuple.of(
                        title,
                        subtitle,
                        content,
                        style,
                        extra,
                        id
                    )
                ).mapEmpty()
        }
    }

}