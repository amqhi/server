package com.amqhi.routes

import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.models.ItemAttributes
import com.amqhi.models.SyncEventType
import com.amqhi.services.AuthService
import com.amqhi.services.FilesService
import com.amqhi.services.SyncEventsService
import com.amqhi.services.UploadedPart
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import java.util.UUID

fun Router.mountFilesRouter(authService: AuthService, syncEventsService: SyncEventsService, filesService: FilesService) {
    post("/files").handler { context ->
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            filesService.createFile(
                itemAttributes = ItemAttributes.from(user.id, json),
                mimeType = json.getString("mime_type"),
                size = json.getLong("size")
            ).onSuccess {
                context.response().putHeader("content-type", "application/json").setStatusCode(HttpResponseStatus.CREATED.code()).end(
                    JsonObject()
                        .put("item", it.first.toSummaryJson())
                        .put("upload", it.second)
                        .toString()
                )
            }
                .onFailure {
                    // TODO: Implement onFailure block for POST /files
                    context.response().putHeader("content-type", "text/plain").setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    get("/files/:id").handler { context ->
        context.withAuth(authService) { user ->
            filesService.getFile(
                userId = user.id.toString(),
                itemId = context.pathParam("id")
            )
                .onSuccess { fileItem ->
                    context.response().putHeader("content-type", "application/json").setStatusCode(200).end(
                        fileItem.toJson().toString())
                }
                .onFailure {
                    // TODO: Implement onFailure block for GET /files/:id
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    get("/files/:id/download-url").handler { context ->
        context.withAuth(authService) { user ->
            filesService.downloadFile(
                userId = user.id.toString(),
                itemId = context.pathParam("id")
            )
                .onSuccess { url ->
                context.response().putHeader("content-type", "text/plain").setStatusCode(200).end(
                    url)
            }
                .onFailure {
                    // TODO: Implement onFailure block for GET /files/:id/download-url
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    /**
     * Completes a file upload by merging multipart chunks for large files
     * and saving the file metadata to the database.
     * */
    post("/files/:id/complete").handler { context ->
        val itemId = context.pathParam("id")
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            filesService.completeUpload(
                userId = user.id.toString(),
                itemId = itemId,
                checksum = json.getString("checksum"),
                size = json.getLong("size"),
                mimeType = json.getString("mime_type"),
                uploadId = json.getValue("upload_id") as? String ?: "",
                parts = (json.getValue("parts") as? JsonArray)?.map {
                    if(it is JsonObject) {
                        UploadedPart(
                            eTag = it.getString("etag"),
                            partNumber = it.getInteger("part_number")
                        )
                    }
                    else{
                        UploadedPart(
                            eTag = "1",
                            partNumber = 1
                        )
                    }
                    }?.toList() ?: listOf()
            )
                .onSuccess {
                    syncEventsService.createEvent(
                        itemId = UUID.fromString(itemId),
                        eventType = SyncEventType.CREATE,
                        bitMask = user.deviceBitMask,
                        userId = user.id,
                    )
                        .onComplete {
                            context.success()
                        }
                }
                .onFailure {
                    // TODO: Implement onFailure block for POST /files/:id/complete
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}