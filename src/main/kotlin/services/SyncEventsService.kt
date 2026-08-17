package com.amqhi.services

import com.amqhi.models.SyncEvent
import com.amqhi.models.SyncEventType
import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

class SyncEventsService(private val pool: Pool) {

    fun getEvents(deviceBitMask: Int) : Future<List<SyncEvent>> {
        return pool.preparedQuery("""
            SELECT * FROM sync_events
            WHERE (synced_devices & $1) = 0;
        """.trimIndent())
            .execute(Tuple.of(
                deviceBitMask
            ))
            .map { rows ->
                rows.map { SyncEvent.from(it) }
            }
    }

    fun createEvent(itemId: UUID, eventType: SyncEventType, bitMask: Int, userId: UUID) : Future<SyncEvent> {
        return pool.preparedQuery("""
            INSERT INTO sync_events(item_id, type, synced_devices, occurred_at, user_id) VALUES ($1, $2, $3, NOW(), $4)
                RETURNING *
        """.trimIndent())
            .execute(Tuple.of(
                itemId,
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


    fun consumeEvent(eventId: UUID, bitMask: Int, userId: UUID): Future<Unit> {
        val updateSql = """
        WITH target AS (
            SELECT COALESCE(SUM(bit_mask), 0) AS mask 
            FROM user_devices 
            WHERE user_id = $3
        )
        UPDATE sync_events 
        SET synced_devices = synced_devices | $1 
        WHERE id = $2 AND user_id = $3
        RETURNING synced_devices;
    """.trimIndent()

        return pool.preparedQuery(updateSql)
            .execute(Tuple.of(bitMask, eventId, userId))
            .compose { rowSet ->
                val row = rowSet.first()

                val syncedDevices = row.getInteger("synced_devices")
                val targetMask = row.getInteger("target_mask")

                if (targetMask > 0 && (syncedDevices and targetMask) == targetMask) {
                    val deleteSql = "DELETE FROM sync_events WHERE id = $1"
                    pool.preparedQuery(deleteSql)
                        .execute(Tuple.of(eventId))
                        .mapEmpty()
                } else {
                    Future.succeededFuture()
                }
            }
    }
}