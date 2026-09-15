/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.services

import com.amqhi.models.Folder
import com.amqhi.models.FolderAttributes
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class FoldersService(private val pool: Pool) {

    fun createFolder(userId: UUID, itemAttributes: ItemAttributes, folderAttributes: FolderAttributes) : Future<Folder> {
        return pool.preparedQuery("""
                  WITH inserted_item AS (
                        INSERT INTO items(
                            user_id,
                            type,
                            created_at,
                            updated_at,
                            event_at,
                            parent_id,
                            name,
                            comment,
                            encrypted,
                            app_scope
                        )
                        VALUES (
                            $1, $2, NOW(), NOW(), $3, $4, $5, $6, $7, $8
                        )
                        RETURNING *
                    ),
                    inserted_folder AS (
                        INSERT INTO folders (
                            id,
                            background_id,
                            background_color,
                            icon_id,
                            icon_color
                        )
                        SELECT
                            id,
                            $9,
                            $10,
                            $11,
                            $12
                        FROM inserted_item
                        RETURNING *
                    )
                    SELECT
                        i.*,
                        f.background_id,
                        f.background_color,
                        f.icon_id,
                        f.icon_color
                    FROM inserted_item i
                    JOIN inserted_folder f ON f.id = i.id;
                 """.trimIndent())
            .execute(Tuple.of(userId, ItemType.FOLDER.toString().lowercase(), itemAttributes.eventAt,itemAttributes.parentId, itemAttributes.name, itemAttributes.comment, itemAttributes.encrypted, itemAttributes.appScope, folderAttributes.backgroundId, folderAttributes.backgroundColor?.value, folderAttributes.iconId, folderAttributes.iconColor?.value))
            .map { rows ->
                println(rows.first().toJson())
                Folder.from(rows.first())
            }
    }

}