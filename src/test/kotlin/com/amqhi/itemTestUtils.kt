/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.Item
import com.amqhi.models.ItemType
import com.amqhi.models.appScopeFromJsonArray
import io.vertx.core.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.util.UUID

fun softDeleteTestItem(client: HttpClient, baseUrl: String, itemId: String, accessToken: String) {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/items/$itemId"))
        .headers("Authorization", "Bearer $accessToken")
        .DELETE()
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 200)
}

fun deleteTestItemPermanently(client: HttpClient, baseUrl: String, itemId: String, accessToken: String) {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/items/$itemId/permanent"))
        .headers("Authorization", "Bearer $accessToken")
        .DELETE()
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 200)
}

fun testItemFromJsonObject(jsonObject: JsonObject): Item {
    return Item(
        id = UUID.fromString(jsonObject.getString("id")),
        userId = UUID.randomUUID(),
        type = ItemType.valueOf(jsonObject.getString("type").uppercase()),
        name = jsonObject.getValue("name") as? String,
        createdAt = OffsetDateTime.parse(jsonObject.getString("created_at")),
        updatedAt = OffsetDateTime.parse(jsonObject.getString("updated_at")),
        deletedAt = (jsonObject.getValue("deleted_at") as? String)?.let { OffsetDateTime.parse(it) },
        eventAt = (jsonObject.getValue("event_at") as? String)?.let { OffsetDateTime.parse(it) },
        parentId = (jsonObject.getValue("parent_id") as? String)?.let { UUID.fromString(it) },
        encrypted = jsonObject.getBoolean("encrypted"),
        comment = jsonObject.getValue("comment") as? String,
        appScope = appScopeFromJsonArray(jsonObject.getJsonArray("app_scope"))
    )
}