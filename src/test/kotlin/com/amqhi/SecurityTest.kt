/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.AppType
import io.vertx.core.Vertx
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime

class SecurityTest {

    @Test
    fun revokedAccessTokenCannotBeUsed() {
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

        val session = testLogin(client, baseUrl = baseUrl, user = user, deviceName = "LOWHP Laptop", deviceOs = "windows", appType = AppType.NOTES)

        val logoutRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/auth/logout"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${session.accessToken}")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val logoutResponse = client.send(logoutRequest, HttpResponse.BodyHandlers.ofString())

        assert(logoutResponse.statusCode() == 200)

        val getItemsRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/items"))
            .headers("Authorization", "Bearer ${session.accessToken}")
            .GET()
            .build()

        val getItemsResponse = client.send(getItemsRequest, HttpResponse.BodyHandlers.ofString())

        assert(getItemsResponse.statusCode() == 401)

        val session2 = testLogin(client, baseUrl = baseUrl, user = user, deviceName = "LOWHP Laptop", deviceOs = "windows", appType = AppType.NOTES)

        deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session2.accessToken)
    }

}