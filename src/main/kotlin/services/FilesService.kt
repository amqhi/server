package com.amqhi.services

import com.amqhi.models.FileItem
import com.amqhi.models.Item
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import com.amqhi.utils.getStringOrNull
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.ceil

const val FIVE_MB = 5 * 1024 * 1024L

class FilesService(private val pool: Pool, private val storageService: StorageService) {
    /**
     * Creates item metadata in the database, generates a presigned upload URL,
     * and returns the item along with a JSON object containing upload parameters.
     * */
    fun createFile(itemAttributes: ItemAttributes, mimeType: String, size: Long) : Future<Pair<Item, JsonObject>> {
        return pool.preparedQuery("""
            INSERT INTO items(user_id, type, created_at, updated_at, event_at, parent_id, name, comment, encrypted, app_scope) VALUES ($1, $2, NOW(), NOW(), $3, $4, $5, $6, $7, $8)
                 RETURNING *
                 """.trimIndent())
            .execute(Tuple.of(itemAttributes.userId, ItemType.FILE.toString().lowercase(), itemAttributes.eventAt,itemAttributes.parentId, itemAttributes.name, itemAttributes.comment, itemAttributes.encrypted, itemAttributes.appScope))
            .compose { rows ->
                if(!rows.any()) {
                    // TODO: Replace with domain exception
                    return@compose Future.failedFuture(Exception())
                }
                val item = Item.from(rows.first())
                val key = "${itemAttributes.userId}/${item.id}/original"

                // TODO: Make single vs. multipart upload threshold configurable
                if(size <= FIVE_MB) {
                    storageService.getUploadUrl(
                        key = key,
                        contentType = mimeType,
                        duration = Duration.ofMinutes(5),
                        contentLength = size
                    ).map { url ->
                        Pair(
                            item,
                            JsonObject()
                                .put("type", "single")
                                .put("url", url)
                        )
                    }
                }
                else {
                    storageService.initiateMultipartUpload(key, mimeType).compose { uploadId ->
                        val chunkCount = ceil(size.toDouble() / FIVE_MB).toInt()

                        val futures: List<Future<PresignedPart>> = (1..chunkCount).map { partNum ->
                            storageService.getUploadPartUrl(key, uploadId, partNum, Duration.ofMinutes(15), size)
                        }

                        Future.all<Future<PresignedPart>>(futures).map { compositeResult ->
                            val parts = compositeResult.list<PresignedPart>().sortedBy { it.partNumber }
                            val jsonArray = JsonArray()
                            parts.forEach { part ->
                                jsonArray.add(JsonObject()
                                    .put("part_number", part.partNumber)
                                    .put("url", part.url)
                                    .put("start_offset", part.startOffset)
                                    .put("size", part.size))
                            }
                            Pair(
                                item,
                                JsonObject()
                                    .put("type", "multipart")
                                    .put("upload_id", uploadId)
                                    .put("parts", jsonArray)
                            )
                        }
                    }
                }
            }
    }

    fun getFile(userId: String, itemId: String): Future<FileItem> {
        return pool.preparedQuery("""
            SELECT 
                i.id,
                i.user_id,
                i.name,
                i.created_at,
                i.updated_at,
                i.deleted_at,
                i.event_at,
                i.parent_id,
                i.encrypted,
                i.comment,
                i.app_scope,
                f.checksum,
                f.size,
                f.mime_type
            FROM items i
            INNER JOIN files f ON i.id = f.id
            WHERE i.user_id = $1
              AND i.id = $2
              AND i.type = 'file'
              AND i.deleted_at IS NULL
            ORDER BY i.created_at DESC;
        """.trimIndent())
            .execute(Tuple.of(userId, itemId))
            .map { rows ->
                if (!rows.any()) {
                    // TODO: Use domain specific exception
                   throw NoSuchElementException()
                }
                FileItem.from(rows.first())
            }
    }

    fun downloadFile(userId: String, itemId: String) : Future<String> {
        return pool.preparedQuery("""SELECT mime_type FROM "files" WHERE id = $1""".trimIndent()).execute(Tuple.of(itemId)).compose { rows ->
            if (!rows.any()) {
                return@compose Future.failedFuture(NoSuchElementException())
            }
            storageService.getDownloadUrl(
                key = "$userId/$itemId/original",
                contentType = rows.first().getString("mime_type"),
                duration = Duration.ofMinutes(5)
            )
        }
    }

    // TODO: Fix KeyNotFoundException when thumbnail or file is requested immediately after upload completion.
    fun completeUpload(userId: String, itemId: String, size: Long, checksum: String, mimeType: String, parts: List<UploadedPart>, uploadId: String) : Future<Unit> {
        val key = "$userId/$itemId/original"
        return if(size <= FIVE_MB) {
            completeUpload(userId, itemId, size, checksum, mimeType)
        } else {
            storageService.completeUpload(key, uploadId, parts).compose {
                completeUpload(userId, itemId, size, checksum, mimeType)
            }
        }
    }

    fun completeUpload(userId: String, itemId: String, size: Long, checksum: String, mimeType: String) : Future<Unit> {
        val key = "$userId/$itemId/original"
        return storageService.getObjectMetadata(key).compose { metadata ->
//                    if(metadata.checksum == null || metadata.size == null || metadata.mimeType == null) {
//                        return@compose Future.failedFuture(NoSuchElementException())
//                    }
            return@compose pool.preparedQuery("""
                        INSERT INTO files(id, mime_type, checksum, size) VALUES ($1, $2, $3, $4)
                    """.trimIndent())
                .execute(Tuple.of(itemId, mimeType, checksum, size))
        }.compose {
            pool.preparedQuery("""
                            UPDATE "users"
                            SET "used_storage" = "used_storage" + $1
                            WHERE "id" = $2;
                    """.trimIndent())
                .execute(Tuple.of(size, userId))
        }.mapEmpty()
    }
}