/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.services

import com.amqhi.models.Album
import com.amqhi.models.Alias
import com.amqhi.models.Artist
import com.amqhi.models.FileItem
import com.amqhi.models.Item
import com.amqhi.models.ItemType
import com.amqhi.models.Note
import com.amqhi.models.Song
import com.amqhi.models.SyncEvent
import com.amqhi.models.SyncEventResponse
import com.amqhi.models.SyncEventType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class SyncEventsService(private val pool: Pool) {

    fun getEvents(deviceBitMask: Int, userId: UUID) : Future<List<SyncEventResponse>> {
        return pool.preparedQuery("""
              SELECT * FROM sync_events
            WHERE user_id = $1 AND (synced_devices & $2) = 0
        ORDER BY occurred_at;
    """)
            .execute(Tuple.of(userId, deviceBitMask))
            .map { rows ->
                rows.map {
                    SyncEvent.from(it) }
            }
            .compose { events ->
                val itemIds = events.map {it.itemId}.toTypedArray()
               val noteIds = events.filter { it.itemType == ItemType.NOTE }.map { it.itemId }.toTypedArray()
                val fileIds = events.filter { it.itemType == ItemType.FILE }.map { it.itemId }.toTypedArray()
                val songIds = events.filter { it.itemType == ItemType.SONG }.map { it.itemId }.toTypedArray()
                val albumIds = events.filter { it.itemType == ItemType.ALBUM }.map { it.itemId }.toTypedArray()
                val artistIds = events.filter { it.itemType == ItemType.ARTIST }.map { it.itemId }.toTypedArray()
//                val folderIds = events.filter { it.itemType == ItemType.FOLDER }.map { it.itemId }.toTypedArray()
                val aliasIds = events.filter { it.itemType == ItemType.ALIAS }.map { it.itemId }.toTypedArray()
                val themeIds = events.filter { it.itemType == ItemType.THEME }.map { it.itemId }.toTypedArray()
                val linkIds = events.filter { it.itemType == ItemType.LINK }.map { it.itemId }.toTypedArray()

                val itemsFuture = pool.preparedQuery("""
                        SELECT * FROM items WHERE user_id = $1 AND id = ANY($2);
                """.trimIndent())
                    .execute(Tuple.of(userId, itemIds))
                    .map { rows ->
                        rows.map {
                            Item.from(it)
                        }
                    }

                val notesFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            n.*
                        FROM items i
                        INNER JOIN notes n ON i.id = n.id
                        WHERE i.user_id = $1
                        AND i.id = ANY($2)
                        AND i.type = 'note';
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        noteIds
                    ))
                    .map { rows ->
                        rows.map {
                            Note.from(it)
                        }
                    }
                val filesFuture = pool.preparedQuery(
                    """
                       SELECT
                            i.*,
                            f.*
                        FROM items i
                        INNER JOIN files f ON i.id = f.id
                        WHERE
                        i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'file';
                        """
                        .trimIndent())
                    .execute(Tuple.of(
                        userId,
                        fileIds
                    ))
                    .map { rows ->
                        rows.map {
                            FileItem.from(it)
                        }
                    }
                val songsFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            s.*
                        FROM items i
                        INNER JOIN songs s ON i.id = s.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'song';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        songIds
                    ))
                    .map { rows ->
                        rows.map {
                            Song.from(it)
                        }
                    }
                val albumsFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            a.*
                        FROM items i
                        INNER JOIN albums a ON i.id = a.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'album';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        albumIds
                    ))
                    .map { rows ->
                        rows.map {
                            Album.from(it)
                        }
                    }
                val artistsFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            a.*
                        FROM items i
                        INNER JOIN artists a ON i.id = a.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'artist';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        artistIds
                    ))
                    .map { rows ->
                        rows.map {
                            Artist.from(it)
                        }
                    }
                val aliasesFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            a.*
                        FROM items i
                        INNER JOIN files a ON i.id = a.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'alias';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        aliasIds
                    ))
                    .map { rows ->
                        rows.map {
                            Alias.from(it)
                        }
                    }
                val linksFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            l.*
                        FROM items i
                        INNER JOIN links l ON i.id = l.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'link';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        linkIds
                    ))
                    .map { rows ->
                        rows.map {
                            Note.from(it)
                        }
                    }
                val themesFuture = pool.preparedQuery("""
                     SELECT 
                            i.*,
                            t.*
                        FROM items i
                        INNER JOIN themes t ON i.id = t.id
                        WHERE i.user_id = $1
                          AND i.id = ANY($2)
                          AND i.type = 'theme';  
                """.trimIndent())
                    .execute(Tuple.of(
                        userId,
                        themeIds
                    ))
                    .map { rows ->
                        rows.map {
                            Note.from(it)
                        }
                    }
                Future.all<Void>(listOf(itemsFuture, notesFuture, filesFuture, songsFuture, albumsFuture, linksFuture, themesFuture, artistsFuture, aliasesFuture)).map {
                    val itemsMap = itemsFuture.result().associateBy { it.id }
                    val notesMap = notesFuture.result().associateBy { it.id }
                    val filesMap = filesFuture.result().associateBy { it.id }
                    val songsMap = songsFuture.result().associateBy { it.id }
                    val albumsMap = albumsFuture.result().associateBy { it.id }
                    val linksMap = linksFuture.result().associateBy { it.id }
                    val themesMap = themesFuture.result().associateBy { it.id }
                    val artistsMap = artistsFuture.result().associateBy { it.id }
                    val aliasesMap = aliasesFuture.result().associateBy { it.id }
                    events.map {
                        when(it.itemType) {
                            ItemType.SONG -> {
                                SyncEventResponse(
                                    event = it,
                                    songsMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.ALBUM -> {
                                SyncEventResponse(
                                    event = it,
                                    albumsMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.LINK -> {
                                SyncEventResponse(
                                    event = it,
                                    linksMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.THEME -> {
                                SyncEventResponse(
                                    event = it,
                                    themesMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.ALIAS -> {
                                SyncEventResponse(
                                    event = it,
                                    aliasesMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.ARTIST -> {
                                SyncEventResponse(
                                    event = it,
                                    artistsMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.NOTE -> {
                                SyncEventResponse(
                                    event = it,
                                    notesMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.FILE -> {
                                SyncEventResponse(
                                    event = it,
                                    filesMap[it.itemId]?.toJson() ?: itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            ItemType.FOLDER -> {
                                SyncEventResponse(
                                    event = it,
                                    itemsMap[it.itemId]!!.toJson()
                                )
                            }
                            else -> {
                                SyncEventResponse(
                                    event = it,
                                    itemsMap[it.itemId]!!.toJson()
                                )
                            }
                        }
                    }
                }
            }
    }

    fun createEvent(itemId: UUID, eventType: SyncEventType, bitMask: Int, userId: UUID, itemType: ItemType) : Future<SyncEvent> {
        return pool.preparedQuery("""
            INSERT INTO sync_events(item_id, item_type, type, synced_devices, occurred_at, user_id) VALUES ($1, $2, $3, $4, NOW(), $5)
            ON CONFLICT (item_id) DO UPDATE SET item_type = $2, type = $3, synced_devices = $4, occurred_at = NOW()
                RETURNING *
        """.trimIndent())
            .execute(Tuple.of(
                itemId,
                itemType.toString().lowercase(),
                eventType.toString().lowercase(),
                bitMask,
                userId
            ))
            .map { rows ->
                if(!rows.any()) {
                    // TODO: replace with a better exception
                    throw Exception("OMG")
                }
                SyncEvent.from(rows.first())
            }
    }


    fun acknowledgeEvents(itemIds: Array<UUID>, bitMask: Int, userId: UUID): Future<Unit> {
        return pool.preparedQuery("""
                  WITH target AS (
                    SELECT COALESCE(SUM(bit_mask), 0) AS mask 
                    FROM user_devices 
                    WHERE user_id = $2
                )
                UPDATE sync_events 
                SET synced_devices = synced_devices | $1 
                FROM target
                WHERE user_id = $2 AND item_id = ANY($3)
                RETURNING target.mask AS target_mask;
            """.trimIndent())
            .execute(Tuple.of(bitMask,userId, itemIds))
            .compose { rowSet ->
                val row = rowSet.first()

                val targetMask = row.getInteger("target_mask")

                pool.preparedQuery("DELETE FROM sync_events WHERE user_id = $1 AND synced_devices = $2")
                    .execute(Tuple.of(userId, targetMask))
                    .mapEmpty()
            }
    }
}