/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.models.FileItem
import com.amqhi.models.ItemAttributes
import com.amqhi.models.appScopeToJsonArray
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

fun getFileChecksum(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")

    file.inputStream().use { inputStream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun createTestFile(client: HttpClient, baseUrl: String, accessToken: String, attributes: ItemAttributes, file: File) : FileItem {

    val mimeType = Files.probeContentType(file.toPath())
    val fileChecksum = getFileChecksum(file)

    val body = mapOf(
        "name" to attributes.name,
        "event_at" to attributes.eventAt?.toString(),
        "parent_id" to attributes.parentId?.toString(),
        "encrypted" to attributes.encrypted,
        "comment" to attributes.comment,
        "app_scope" to appScopeToJsonArray(attributes.appScope),
        "mime_type" to mimeType,
        "size" to file.length()
    )
    val request = HttpRequest.newBuilder()
        .uri(URI.create("${baseUrl}/files"))
        .headers("Authorization", "Bearer $accessToken")
        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(body)))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assert(response.statusCode() == 201)

    val responseBody = JsonObject(response.body())
    val upload = responseBody.getJsonObject("upload")

    val item = testItemFromJsonObject(responseBody.getJsonObject("item"))

    var parts: List<Map<String, Any>>? = null
    var uploadId: String? = null

    // Upload the file to object storage
    if(upload.getString("type") == "single") {
        val url = upload.getString("url")
        val fileUploadRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", Files.probeContentType(file.toPath()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(file.readBytes()))
            .build()

        val fileUploadResponse = client.send(fileUploadRequest, HttpResponse.BodyHandlers.ofString())
        assert(fileUploadResponse.statusCode() == 200)
    }
    else {
        uploadId = upload.getString("upload_id")
        val uploadedParts = mutableListOf<Map<String, Any>>()
        val uploadParts = upload.getJsonArray("parts")
        for (i in 0 until uploadParts.size()) {
            val part = uploadParts.getJsonObject(i)
            val startOffset = part.getLong("start_offset")
            val partSize = part.getLong("size").toInt()
            val partBytes = ByteArray(partSize)
            RandomAccessFile(file, "r").use { randomAccessFile ->
                randomAccessFile.seek(startOffset)
                randomAccessFile.readFully(partBytes)
            }
            val partUploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(part.getString("url")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(partBytes))
                .build()
            val partUploadResponse = client.send(partUploadRequest, HttpResponse.BodyHandlers.ofString())
            assert(partUploadResponse.statusCode() == 200)
            uploadedParts.add(
                mapOf(
                    "part_number" to part.getInteger("part_number"),
                    "etag" to partUploadResponse.headers().firstValue("etag").get()
                )
            )
        }
        parts = uploadedParts
    }
    val uploadCompletionBody = mapOf(
        "checksum" to fileChecksum,
        "size" to file.length(),
        "mime_type" to Files.probeContentType(file.toPath()),
        "upload_id" to uploadId,
        "parts" to parts
    )
    val uploadCompletionRequest = HttpRequest.newBuilder()
        .uri(URI.create("$baseUrl/files/${item.id}/complete"))
        .headers("Authorization", "Bearer $accessToken")
        .POST(HttpRequest.BodyPublishers.ofString(Json.encode(uploadCompletionBody)))
        .build()

    val uploadCompletionResponse = client.send(uploadCompletionRequest, HttpResponse.BodyHandlers.ofString())
    assert(uploadCompletionResponse.statusCode() == 200)

    return uploadCompletionResponse.body().let {
        FileItem(
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
            checksum = fileChecksum,
            size = file.length(),
            mimeType = mimeType
        )
    }
}