/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.Color
import com.amqhi.models.Folder
import com.amqhi.models.FolderAttributes
import com.amqhi.models.ItemAttributes
import com.amqhi.models.appScopeToJsonArray
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

fun createTestFolder(client: HttpClient, baseUrl: String, accessToken: String, attributes: ItemAttributes, folderAttributes: FolderAttributes) : Folder {
    val body = mapOf(
        "name" to attributes.name,
        "event_at" to attributes.eventAt?.toString(),
        "parent_id" to attributes.parentId?.toString(),
        "encrypted" to attributes.encrypted,
        "comment" to attributes.comment,
        "app_scope" to appScopeToJsonArray(attributes.appScope),
        "background_id" to folderAttributes.backgroundId?.toString(),
        "background_color" to folderAttributes.backgroundColor?.toString(),
        "icon_id" to folderAttributes.iconId?.toString(),
        "icon_color" to folderAttributes.iconColor?.toString()
    )
    val request = HttpRequest.newBuilder()
        .uri(URI.create("${baseUrl}/folders"))
        .headers("Authorization", "Bearer $accessToken")
        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(body)))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 201)

    val responseBody = JsonObject(response.body())
    val item = testItemFromJsonObject(responseBody)

    return Folder(
        id = item.id,
        userId = UUID.randomUUID(),
        type = item.type,
        name = item.name,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
        deletedAt = item.deletedAt,
        eventAt = item.eventAt,
        parentId = item.parentId,
        encrypted = item.encrypted,
        comment = item.comment,
        appScope = item.appScope,
        backgroundId = (responseBody.getValue("background_id") as? String)?.let { UUID.fromString(it) },
        backgroundColor = (responseBody.getValue("background_color") as? String)?.let { Color.fromString(it) },
        iconId = (responseBody.getValue("icon_id") as? String)?.let { UUID.fromString(it) },
        iconColor = (responseBody.getValue("icon_color") as? String)?.let { Color.fromString(it) }
    )
}