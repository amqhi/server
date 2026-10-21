/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.common.LogConfig
import com.amqhi.models.AppType
import com.amqhi.models.ItemAttributes
import com.amqhi.models.appScopeFromJsonArray
import com.amqhi.models.appScopeToJsonArray
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class FileSyncTest {

        @Test
        fun `image file should produce correct sync event counts across sessions`() {
            LogConfig.setup()
            val vertx = Vertx.vertx()
            vertx.deployVerticle(App()).await()

            val baseUrl = "http://localhost:${System.getenv("PORT")?.toInt() ?: 8000}"

            val client = HttpClient.newHttpClient()

            val now = LocalDateTime.now()

            val user = TestUser(
                email = "me${now.second}@amqhi.com",
                password = "1234",
                name = "me",
            )

            createTestUser(client = client, baseUrl = baseUrl, user = user)
            val session1 = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "Goggle Fixel", deviceOs = "android", appType = AppType.CLOUD)
            val session2 = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "Microshift Surface", deviceOs = "windows", appType = AppType.CLOUD)
            val session3 = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "Mocbook Pro", deviceOs = "macos", appType = AppType.CLOUD)

            val imageFile = File(System.getenv("IMAGE_FILE_PATH") ?: "path/to/image.png")

            val createdImage = createTestFile(client = client, baseUrl = baseUrl, attributes = ItemAttributes(
                name = "image.png",
                eventAt = null,
                parentId = null,
                encrypted = false,
                comment = null,
                appScope = appScopeFromJsonArray(JsonArray().add("cloud").add("notes").add("music").add("photos").add("web").add("ai"))
            ), accessToken = session1.accessToken,
                file = imageFile)

            val session2SyncEvents = getTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session2.accessToken)
            assert(session2SyncEvents.events.size == 1)
            assert(session2SyncEvents.events[0].item.getString("name") == "image.png")
            assert(session2SyncEvents.events[0].item.getLong("size") == imageFile.length())
            println(session2SyncEvents.events[0].item)

            acknowledgeTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session2.accessToken, itemIds = session2SyncEvents.events.map { UUID.fromString(it.item.getString("id")) }.toTypedArray())


            val session2SyncEvents2 = getTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session2.accessToken)
            assert(session2SyncEvents2.events.isEmpty())


            val session3SyncEvents = getTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session3.accessToken)
            assert(session3SyncEvents.events.size == 1)


            acknowledgeTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session3.accessToken, itemIds = session3SyncEvents.events.map { UUID.fromString(it.item.getString("id")) }.toTypedArray())

            val session3SyncEvents2 = getTestSyncEvents(client = client, baseUrl = baseUrl, accessToken = session3.accessToken)
            assert(session3SyncEvents2.events.isEmpty())

            softDeleteTestItem(client = client, baseUrl = baseUrl, itemId = createdImage.id.toString(), accessToken = session1.accessToken)

            deleteTestItemPermanently(client = client, baseUrl = baseUrl, itemId = createdImage.id.toString(), accessToken = session1.accessToken)

            deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session1.accessToken)
        }

    @Test
    fun `unfinished upload should be reused when creating the same file name`() {
        LogConfig.setup()
        val vertx = Vertx.vertx()
        vertx.deployVerticle(App()).await()

        val baseUrl = "http://localhost:${System.getenv("PORT")?.toInt() ?: 8000}"

        val client = HttpClient.newHttpClient()

        val now = LocalDateTime.now()

        val user = TestUser(
            email = "me${now.second}@amqhi.com",
            password = "1234",
            name = "me",
        )

        createTestUser(client = client, baseUrl = baseUrl, user = user)
        val session = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "Goggle Fixel", deviceOs = "android", appType = AppType.CLOUD)

        val file = File(System.getenv("IMAGE_FILE_PATH") ?: "path/to/image.png")

        val mimeType = Files.probeContentType(file.toPath())
        val itemAttributes = ItemAttributes(
            name = "image.png",
            eventAt = null,
            parentId = null,
            encrypted = false,
            comment = null,
            appScope = appScopeFromJsonArray(JsonArray().add("cloud").add("notes").add("music").add("photos").add("web").add("ai"))
        )

        val fileCreateRequestBody = mapOf(
            "name" to itemAttributes.name,
            "event_at" to itemAttributes.eventAt?.toString(),
            "parent_id" to itemAttributes.parentId?.toString(),
            "encrypted" to itemAttributes.encrypted,
            "comment" to itemAttributes.comment,
            "app_scope" to appScopeToJsonArray(itemAttributes.appScope),
            "mime_type" to mimeType,
            "size" to file.length()
        )

        var firstCreatedAt: OffsetDateTime? = null

        HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl}/files"))
            .headers("Authorization", "Bearer ${session.accessToken}")
            .POST(HttpRequest.BodyPublishers.ofString(Json.encode(fileCreateRequestBody)))
            .build().let { request ->
                client.send(request, HttpResponse.BodyHandlers.ofString()).let { response ->
                    assert(response.statusCode() == 201)

                    val responseBody = JsonObject(response.body())
                    testItemFromJsonObject(responseBody.getJsonObject("item")).let {
                        firstCreatedAt = it.createdAt
                    }
                    val upload = responseBody.getJsonObject("upload")
                    val url = upload.getString("url")
                    val fileUploadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .headers("Content-Type", "INVALID_TYPE!!!")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file.readBytes()))
                        .build()

                    val fileUploadResponse = client.send(fileUploadRequest, HttpResponse.BodyHandlers.ofString())

                    assert(fileUploadResponse.statusCode() != 200)
                }
            }

        HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl}/files"))
            .headers("Authorization", "Bearer ${session.accessToken}")
            .POST(HttpRequest.BodyPublishers.ofString(Json.encode(fileCreateRequestBody)))
            .build().let { request ->
                client.send(request, HttpResponse.BodyHandlers.ofString()).let { response ->
                    assert(response.statusCode() == 201)

                    val responseBody = JsonObject(response.body())
                    val item = testItemFromJsonObject(responseBody.getJsonObject("item"))
                    assert(firstCreatedAt != null && firstCreatedAt != item.createdAt)
                    val upload = responseBody.getJsonObject("upload")
                    val url = upload.getString("url")
                    val fileUploadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .headers("Content-Type", Files.probeContentType(file.toPath()))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(file.readBytes()))
                        .build()

                    val fileUploadResponse = client.send(fileUploadRequest, HttpResponse.BodyHandlers.ofString())
                    assert(fileUploadResponse.statusCode() == 200)

                    val uploadCompletionBody = mapOf(
                        "checksum" to getFileChecksum(file),
                        "size" to file.length(),
                        "mime_type" to Files.probeContentType(file.toPath())
                    )
                    val uploadCompletionRequest = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/files/${item.id}/complete"))
                        .headers("Authorization", "Bearer ${session.accessToken}")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(uploadCompletionBody)))
                        .build()

                    val uploadCompletionResponse = client.send(uploadCompletionRequest, HttpResponse.BodyHandlers.ofString())
                    assert(uploadCompletionResponse.statusCode() == 200)

                    softDeleteTestItem(client = client, baseUrl = baseUrl, itemId = item.id.toString(), accessToken = session.accessToken)

                    deleteTestItemPermanently(client = client, baseUrl = baseUrl, itemId = item.id.toString(), accessToken = session.accessToken)
                }
            }

        deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session.accessToken)
    }

}
