/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.SyncEventType
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.util.UUID

data class TestSyncEvent(
    val type: SyncEventType,
    val occurredAt: OffsetDateTime,
    val item: JsonObject
) {
    companion object {
        fun fromJson(jsonObject: JsonObject): TestSyncEvent {
            return TestSyncEvent(
                type = SyncEventType.valueOf(jsonObject.getString("type").uppercase()),
                occurredAt = OffsetDateTime.parse(jsonObject.getString("occurred_at")),
                item = jsonObject.getJsonObject("item")
            )
        }
    }
}

data class TestSyncEventsResponse(
    val hasMore: Boolean,
    val events: List<TestSyncEvent>
) {
    companion object {
        fun fromJson(jsonObject: JsonObject): TestSyncEventsResponse {
            return TestSyncEventsResponse(
                hasMore = jsonObject.getBoolean("has_more"),
                events = jsonObject.getJsonArray("events").map { TestSyncEvent.fromJson(it as JsonObject) }
            )
        }
    }
}

fun getTestSyncEvents(client: HttpClient, baseUrl: String, accessToken: String): TestSyncEventsResponse {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/sync/events"))
        .headers("Authorization", "Bearer $accessToken")
        .GET()
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    assert(response.statusCode() == 200)

    return TestSyncEventsResponse.fromJson(JsonObject(response.body()))
}

fun acknowledgeTestSyncEvents(client: HttpClient, baseUrl: String, itemIds: Array<UUID>, accessToken: String) {
    val itemIdsJsonArray = JsonArray()
    itemIds.forEach { id ->
        itemIdsJsonArray.add(id.toString())
    }
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/sync/events/acknowledge"))
        .headers("Authorization", "Bearer $accessToken")
        .POST(HttpRequest.BodyPublishers.ofString(JsonObject()
            .put("item_ids", itemIdsJsonArray)
            .toString()))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    assert(response.statusCode() == 200)
}
