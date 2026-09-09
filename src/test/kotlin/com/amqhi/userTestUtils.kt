/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class TestUser(
    var email: String,
    var password: String,
    var name: String
)

data class TestSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String,
    val deviceName: String,
    val deviceOs: String
)

fun createTestUser(client: HttpClient, baseUrl: String, user: TestUser) {
    val registerData = mapOf(
        "email" to user.email,
        "password" to user.password,
        "name" to user.name
    )

    val registerRequest = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/users"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(registerData)))
        .build()

    val registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString())

    assert(registerResponse.statusCode() == 200)
}

fun testLogin(client: HttpClient, baseUrl: String, user: TestUser, deviceName: String, deviceOs: String) : TestSession {
    val loginBody = mapOf(
        "email" to user.email,
        "password" to user.password,
        "device_name" to deviceName,
        "device_os" to deviceOs
    )

    val loginRequest = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(loginBody)))
        .build()

    val loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString())

    assert(loginResponse.statusCode() == 200)

    val loginResponseBody = JsonObject(loginResponse.body())
    val accessToken = loginResponseBody.getString("access_token")
    val refreshToken = loginResponseBody.getString("refresh_token")
    val expiresIn = loginResponseBody.getLong("expires_in")
    val tokenType = loginResponseBody.getString("token_type")

    return TestSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresIn,
        tokenType = tokenType,
        deviceName = deviceName,
        deviceOs = deviceOs
    )
}

fun deleteTestUser(client: HttpClient, baseUrl: String, accessToken: String) {
    val deleteRequest = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/users"))
        .headers("Authorization", "Bearer $accessToken")
        .DELETE()
        .build()

    val deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString())

    assert(deleteResponse.statusCode() == 200)
}

