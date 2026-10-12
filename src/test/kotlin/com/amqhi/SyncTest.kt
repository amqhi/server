/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.common.LogConfig
import com.amqhi.models.AppType
import com.amqhi.models.FolderAttributes
import com.amqhi.models.ItemAttributes
import com.amqhi.models.appScopeFromJsonArray
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import org.junit.jupiter.api.Test
import java.io.File
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.UUID

class SyncTest {

    @Test
    fun `multiple items are moved, trashed, restored, and deleted across two sessions`() {
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
        val session1 = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "ASIUUUUS Desktop", deviceOs = "windows", appType = AppType.NOTES)
        val session2 = testLogin(client = client, baseUrl = baseUrl, user = user, deviceName = "Samesung Galaxi S1", deviceOs = "android", appType = AppType.PHOTOS)

        val itemIds = mutableListOf<UUID>()
        val folderIdsToMove = mutableListOf<UUID>()
        var targetFolderId: UUID? = null

        // ==================== create ====================

        for (i in 0..21) {
            if(i <= 10) {
                val imageFile = File(System.getenv("IMAGE_FILE_PATH") ?: "path/to/image.png")
                val createdFile = createTestFile(client = client, baseUrl = baseUrl, attributes = ItemAttributes(
                    name = "image$i.png",
                    eventAt = null,
                    parentId = null,
                    encrypted = false,
                    comment = null,
                    appScope = appScopeFromJsonArray(JsonArray().add("cloud").add("notes").add("music").add("photos").add("web").add("ai"))
                ), accessToken = session1.accessToken,
                    file = imageFile)
                itemIds.add(createdFile.id)
                continue
            }
            if(i <= 20) {
                val folder = createTestFolder(client = client, baseUrl = baseUrl, attributes = ItemAttributes(
                    name = "folder$i",
                    eventAt = null,
                    parentId = null,
                    encrypted = false,
                    comment = null,
                    appScope = appScopeFromJsonArray(JsonArray().add("cloud").add("notes").add("music").add("photos").add("web").add("ai"))
                ), accessToken = session1.accessToken,
                    folderAttributes = FolderAttributes(
                        backgroundId = null,
                        backgroundColor = null,
                        iconId = null,
                        iconColor = null
                    ))
                if(i < 18) {
                    folderIdsToMove.add(folder.id)
                }
                if(i == 18) {
                    targetFolderId = folder.id
                }
                itemIds.add(folder.id)
                continue
            }
            // TODO: add test logic for remaining item type: SyncTest.`multiple items are moved, trashed, restored, and deleted across two sessions`
        }

        acknowledgeTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            itemIds = itemIds.toTypedArray(),
            accessToken = session2.accessToken
        )

        // ==================== move ====================

        moveTestItems(
            client = client,
            baseUrl = baseUrl,
            itemIds = folderIdsToMove.toTypedArray(),
            parentId = targetFolderId!!,
            accessToken = session1.accessToken
        )

        val session1SyncEventsMove = getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session1.accessToken
        )

        assert(session1SyncEventsMove.events.isEmpty())

        val session2SyncEventsMove = getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session2.accessToken
        )

        assert(session2SyncEventsMove.events.size == 7)

        acknowledgeTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            itemIds = session2SyncEventsMove.events.map { UUID.fromString(it.item.getString("id")) }.toTypedArray(),
            accessToken = session2.accessToken
        )

        // ==================== trash ====================

        trashTestItems(
            client = client,
            baseUrl = baseUrl,
            itemIds = itemIds.toTypedArray(),
            accessToken = session2.accessToken
        )

        val session2SyncEventsTrash = getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session2.accessToken
        )

        assert(session2SyncEventsTrash.events.isEmpty())

        val session1SyncEventsTrash = getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session1.accessToken
        )

        assert(session1SyncEventsTrash.events.size == 21)

        acknowledgeTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            itemIds = session1SyncEventsTrash.events.map { UUID.fromString(it.item.getString("id")) }.toTypedArray(),
            accessToken = session1.accessToken
        )

        // ==================== restore ====================

        restoreTestItems(
            client = client,
            baseUrl = baseUrl,
            itemIds = itemIds.toTypedArray(),
            accessToken = session2.accessToken
        )

        getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session1.accessToken
        ).let {
            assert(it.events.size == 21)
            acknowledgeTestSyncEvents(
                client = client,
                baseUrl = baseUrl,
                itemIds = it.events.map { event -> UUID.fromString(event.item.getString("id")) }.toTypedArray(),
                accessToken = session1.accessToken
            )
        }

        getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session2.accessToken
        ).let {
            assert(it.events.isEmpty())
        }

        // ==================== trash and delete permanently ====================

        trashTestItems(
            client = client,
            baseUrl = baseUrl,
            itemIds = itemIds.toTypedArray(),
            accessToken = session2.accessToken
        )

        permanentDeleteTestItems(
            client = client,
            baseUrl = baseUrl,
            itemIds = itemIds.toTypedArray(),
            accessToken = session2.accessToken
        )

        getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session1.accessToken
        ).let { response ->
            println(response.events.size)
            assert(response.events.size == 21)
            acknowledgeTestSyncEvents(
                client = client,
                baseUrl = baseUrl,
                itemIds = response.events.map { UUID.fromString(it.item.getString("id")) }.toTypedArray(),
                accessToken = session1.accessToken
            )
        }

        getTestSyncEvents(
            client = client,
            baseUrl = baseUrl,
            accessToken = session2.accessToken
        ).let {
            assert(it.events.isEmpty())
        }

        deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session1.accessToken)
    }

}