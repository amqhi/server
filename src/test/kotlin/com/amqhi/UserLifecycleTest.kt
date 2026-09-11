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

class UserLifecycleTest {

        @Test
        fun userLifecycleTest() {
            val vertx = Vertx.vertx()
            vertx.deployVerticle(App()).await()

            val baseUrl = "http://localhost:${System.getenv("PORT")?.toInt() ?: 8000}"

            val client = HttpClient.newHttpClient()

            val user = TestUser(
                email = "me123@amqhi.com",
                password = "1234",
                name = "me",
            )
            createTestUser(client = client, baseUrl = baseUrl, user = user)

            val session = testLogin(client, baseUrl = baseUrl, user = user, deviceName = "ENIAC", deviceOs = "windows", appType = AppType.CLOUD)

            val logoutRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/auth/logout"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${session.accessToken}")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()

            val logoutResponse = client.send(logoutRequest, HttpResponse.BodyHandlers.ofString())

            assert(logoutResponse.statusCode() == 200)

            val ensureLoggedOutRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/users/me"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${session.accessToken}")
                .GET()
                .build()

            val ensureLoggedOutResponse = client.send(ensureLoggedOutRequest, HttpResponse.BodyHandlers.ofString())
            assert(ensureLoggedOutResponse.statusCode() != 200)

            deleteTestUser(client = client, baseUrl = baseUrl, accessToken = session.accessToken)

        }
}
