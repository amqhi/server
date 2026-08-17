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
import java.time.OffsetDateTime
import java.util.*
import kotlin.NoSuchElementException
import kotlin.io.path.Path

class ItemsService(private val pool: Pool, private val storageService: StorageService, private val fileProcessingService: FileProcessingService) {
    fun getItems(userId: UUID, parentId: UUID?, status: String?) : Future<List<ItemCore>> {
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

    fun updateItem(id: UUID, type: ItemType?, itemAttributes: ItemAttributes): Future<Void> {
        return updateItem(pool, id, type, itemAttributes).mapEmpty()
    }

    fun restoreItem(id: UUID, userId: UUID) : Future<UUID?> {
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
            RETURNING "parent_id"
        """.trimIndent())
            .execute(Tuple.of(id, userId))
            .map {
                it.firstOrNull()?.getValue("parent_id") as? UUID
            }
    }

    fun softDeleteItem(id: UUID, userId: UUID): Future<Void> {
        return pool.preparedQuery("""
            UPDATE "items" SET "deleted_at" = NOW() WHERE "id" = $1 AND "user_id" = $2
        """.trimIndent())
            .execute(Tuple.of(id, userId)).mapEmpty()
    }

    fun deleteItem(id: UUID, userId: UUID): Future<Void> {
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
                pool.preparedQuery("DELETE FROM items WHERE id = $1 AND user_id = $2 AND deleted_at IS NOT NULL")
                    .execute(Tuple.of(id, userId))
            }
            .mapEmpty()
    }

    fun moveItem(id: UUID, userId: UUID, parentId: UUID?): Future<Void> {
        return pool.preparedQuery("""
            UPDATE "items" SET "parent_id" = $1 WHERE "id" = $2 AND "user_id" = $3
        """.trimIndent())
            .execute(Tuple.of(parentId, id, userId)).mapEmpty()
    }
}