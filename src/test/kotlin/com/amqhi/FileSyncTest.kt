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
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import org.junit.jupiter.api.Test
import java.io.File
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.UUID

class FileSyncTest {

        @Test
        fun test1() {
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

}
