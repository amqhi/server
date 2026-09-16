/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.services

import com.amqhi.common.updateItem
import com.amqhi.models.Item
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemCore
import com.amqhi.models.ItemType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.time.Duration
import java.util.*
import kotlin.io.path.Path

class ItemsService(private val pool: Pool, private val storageService: StorageService, private val fileProcessingService: FileProcessingService) {
    fun getItems(userId: UUID, parentId: UUID?, status: String?) : Future<List<ItemCore>> {
        if((status == "all" || status == null) && parentId == null) {
            return pool.preparedQuery("SELECT * FROM items WHERE user_id = $1").execute(Tuple.of(userId)).map { rows ->
                rows.map { row ->
                    Item.from(row)
                }.toList()
            }
        }

        val statusSQL = when(status) {
            "active" -> "deleted_at IS NULL"
            "deleted" -> "deleted_at IS NOT NULL"
            "all" -> ""
            else -> "deleted_at IS NULL"
        }

        if(parentId == null) {
            return pool.preparedQuery("SELECT * FROM items WHERE user_id = $1 AND parent_id IS NULL AND $statusSQL ORDER BY id DESC").execute(Tuple.of(userId)).map { rows ->
                rows.map { row ->
                    Item.from(row)
                }.toList()
            }
        }

        return pool.preparedQuery("SELECT * FROM items WHERE user_id = $1 AND parent_id = $2 AND $statusSQL ORDER BY id DESC").execute(Tuple.of(userId, parentId)).map { rows ->
            rows.map { row ->
                Item.from(row)
            }.toList()
        }
    }

    fun getItem(id: UUID): Future<Item> {
        return pool.preparedQuery("SELECT * FROM items WHERE id = $1").execute(Tuple.of(id)).map {
            Item.from(it.first())
        }
    }

    fun downloadThumbnail(userId: String, itemId: String) : Future<String> {
        return storageService.getObjectMetadata("$userId/$itemId/thumbnail")
            .onFailure {
                storageService.getObjectMetadata("$userId/$itemId/original").compose { metadata ->
                    println(metadata)
                    if(metadata.mimeType != null && (metadata.mimeType.startsWith("video/") || metadata.mimeType.startsWith("image/"))) {
                        val originalTempPath = Path("tmp", userId, itemId, "original.${metadata.mimeType.split("/").last()}")
                        val thumbnailTempPath = Path("tmp", userId, itemId, "thumbnail.jpg")
                        fileProcessingService.prepareTempDirectory(Path("tmp", userId, itemId).toString()).compose {
                            storageService.getObject("$userId/$itemId/original", originalTempPath).compose {
                                fileProcessingService.generateThumbnail(
                                    inputPath = originalTempPath.toString(),
                                    outputPath = thumbnailTempPath.toString()
                                ).compose {
                                        storageService.putObject(
                                            key = "$userId/$itemId/thumbnail",
                                            path = thumbnailTempPath,
                                            contentType = "image/jpg"
                                        )
                                    }
                                    .compose {
                                        fileProcessingService.deleteTemporaryFolder(Path("tmp", userId, itemId).toString())
                                    }
                            }
                        }
                    }
                    else {
                        return@compose Future.succeededFuture()
                    }
                }
            }.compose {
            storageService.getDownloadUrl(
                key = "$userId/$itemId/thumbnail",
                contentType = "image/jpg",
                duration = Duration.ofHours(1)
            )
        }
    }

    fun updateItem(id: UUID, type: ItemType?, userId: UUID, itemAttributes: ItemAttributes): Future<ItemType> {
        return pool.preparedQuery("""
            UPDATE "items" 
            SET "name" = COALESCE($1, "name"), 
            "type" = COALESCE($2, "type"),
            "updated_at" = NOW(),
            "event_at" = COALESCE($3, "event_at"),
            "parent_id" = COALESCE($4, "parent_id"),
            "encrypted" = COALESCE($5, "encrypted")
            WHERE "id" = $6 AND "user_id" = $7
            RETURNING "type"
        """.trimIndent())
            .execute(Tuple.of(itemAttributes.name,type?.toString()?.lowercase(), itemAttributes.eventAt, itemAttributes.parentId, itemAttributes.encrypted, id, userId))
            .map {
                ItemType.valueOf(it.first().getString("type").uppercase())
            }
    }

    data class RestoreItemResult(
        val parentId: UUID?,
        val itemType: ItemType
    )

    fun restoreItem(id: UUID, userId: UUID) : Future<RestoreItemResult> {
        return pool.preparedQuery("""
            UPDATE "items" AS i
            SET 
                "deleted_at" = NULL,
                "parent_id" = CASE 
                    WHEN EXISTS (
                        SELECT 1 
                        FROM "items" p 
                        WHERE p."id" = i."parent_id" AND p."deleted_at" IS NOT NULL
                    ) THEN NULL
                    ELSE i."parent_id"
                END
            WHERE i."id" = $1 AND i."user_id" = $2
            RETURNING "parent_id", "type"
        """.trimIndent())
            .execute(Tuple.of(id, userId))
            .map {
                RestoreItemResult(
                    parentId = it.firstOrNull()?.getValue("parent_id") as? UUID,
                    itemType = ItemType.valueOf(it.first().getString("type").uppercase())
                )
            }
    }

    fun softDeleteItem(id: UUID, userId: UUID): Future<ItemType> {
        return pool.preparedQuery("""
            UPDATE "items" SET "deleted_at" = NOW() WHERE "id" = $1 AND "user_id" = $2
            RETURNING "type"
        """.trimIndent())
            .execute(Tuple.of(id, userId)).map {
                ItemType.valueOf(it.first().getString("type").uppercase())
            }
    }

    fun deleteItem(id: UUID, userId: UUID): Future<ItemType> {
        return storageService.getObjectMetadata("$userId/$id/thumbnail")
            .compose { storageService.delete("$userId/$id/thumbnail") }
            .recover {
                Future.succeededFuture()
            }
            .compose{ storageService.getObjectMetadata("$userId/$id/original") }
            .compose { metadata ->
            storageService.delete("$userId/$id/original").compose {
                pool.preparedQuery("""
                            UPDATE "users"
                            SET "used_storage" = "used_storage" - $1
                            WHERE "id" = $2;
                    """.trimIndent())
                    .execute(Tuple.of(metadata.size!!, userId))
            } }
            .recover {
                Future.succeededFuture()
            }
            .compose{
                pool.preparedQuery("DELETE FROM items WHERE id = $1 AND user_id = $2 AND deleted_at IS NOT NULL RETURNING type")
                    .execute(Tuple.of(id, userId))
            }
            .map {
                ItemType.valueOf(it.first().getString("type").uppercase())
            }
    }

    fun moveItem(id: UUID, userId: UUID, parentId: UUID?): Future<ItemType> {
        return pool.preparedQuery("""
            UPDATE "items" SET "parent_id" = $1 WHERE "id" = $2 AND "user_id" = $3
            RETURNING "type"
        """.trimIndent())
            .execute(Tuple.of(parentId, id, userId)).map{
                ItemType.valueOf(it.first().getString("type").uppercase())
            }
    }
}