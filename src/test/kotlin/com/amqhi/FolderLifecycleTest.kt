/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.AppType
import com.amqhi.models.FolderAttributes
import com.amqhi.models.ItemAttributes
import com.amqhi.models.appScopeFromJsonArray
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.LocalDateTime

class FolderLifecycleTest {

    @Test
    fun folderLifecycleTest() {
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

        val session = testLogin(client, baseUrl = baseUrl, user = user, deviceName = "Lenove Thinktwice", deviceOs = "linux", appType = AppType.CLOUD)

        val folder = createTestFolder(client = client, baseUrl = baseUrl, accessToken = session.accessToken, attributes = ItemAttributes(
            name = "Folder",
            eventAt = null,
            parentId = null,
            encrypted = false,
            comment = null,
            appScope = appScopeFromJsonArray(JsonArray().add("cloud").add("notes").add("music").add("photos").add("web").add("ai"))
        ), folderAttributes = FolderAttributes(
            backgroundId = null,
            backgroundColor = null,
            iconId = null,
            iconColor = null
        ))

        softDeleteTestItem(client = client, baseUrl = baseUrl, itemId = folder.id.toString(), accessToken = session.accessToken)

        deleteTestItemPermanently(client = client, baseUrl = baseUrl, itemId = folder.id.toString(), accessToken = session.accessToken)

        deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session.accessToken)
    }

}