package com.amqhi.routes

import com.amqhi.common.notFound
import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import com.amqhi.models.SyncEventType
import com.amqhi.services.AuthService
import com.amqhi.services.ItemsService
import com.amqhi.services.SyncEventsService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import java.util.UUID

fun Router.mountItemsRouter(
    authService: AuthService,
    syncEventsService: SyncEventsService,
    itemsService: ItemsService
) {
    get("/items").handler { context ->
        context.withAuth(authService) { user ->
            val parentId: String? = context.request().getParam("parent_id")
            val status: String? = context.request().getParam("status")
            itemsService.getItems(userId = user.id, parentId = parentId?.let { UUID.fromString(it) }, status = status)
                .onSuccess { items ->
                    context.response().setStatusCode(200).end(
                        "[${
                            items.joinToString(",") { item ->
                                item.toSummaryJson().toString()
                            }
                        }]"
                    )
                }.onFailure {
                    // TODO: Implement onFailure block for GET /items
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
            }
        }
    }

    get("/items/:id").handler { context ->
        context.withAuth(authService) {
            itemsService.getItem(UUID.fromString(context.pathParam("id")))
                .onSuccess { item ->
                    context.response().putHeader("Content-Type", "application/json").setStatusCode(200)
                        .end(item.toJson().toString())
                }
                .onFailure {
                    context.notFound()
                }
        }
    }

    get("/items/:id/thumbnail/download-url").handler { context ->
        context.withAuth(authService) { user ->
            itemsService.downloadThumbnail(
                userId = user.id.toString(),
                itemId = context.pathParam("id")
            ).onSuccess { url ->
                context.response().putHeader("content-type", "text/plain").setStatusCode(200).end(
                    url
                )
            }
                .onFailure { throwable ->
                    // TODO: Implement onFailure block for GET /items/:id/thumbnail/download-url
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    patch("/items/:id").handler { context ->
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            val itemId = UUID.fromString(context.pathParam("id"))
            itemsService.updateItem(
                id = itemId,
                type = (json.getValue("type") as? String)?.let { ItemType.valueOf(it.uppercase()) },
                itemAttributes = ItemAttributes.from(user.id, json)
            )
                .onSuccess {
                    syncEventsService.createEvent(
                        itemId = itemId,
                        eventType = SyncEventType.UPDATE,
                        bitMask = user.deviceBitMask,
                        userId = user.id,
                    ).onComplete {
                        context.success()
                    }
                }
                .onFailure { throwable ->
                    // TODO: Implement onFailure block for PATCH /items/:id/move
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    patch("/items/:id/restore").handler { context ->
        context.withAuth(authService) { user ->
            val itemId = UUID.fromString(context.pathParam("id"))
            itemsService.restoreItem(
                id = itemId,
                userId = user.id
            )
                .onSuccess { parentId ->
                    syncEventsService.createEvent(
                        itemId = itemId,
                        eventType = SyncEventType.RESTORE,
                        bitMask = user.deviceBitMask,
                        userId = user.id,
                    ).onComplete {
                        val json = JsonObject()
                        json.put("parent_id", parentId)
                        context.response().putHeader("Content-Type", "application/json").setStatusCode(200)
                            .end(json.toString())
                    }
                }
                .onFailure { throwable ->
                    throwable.printStackTrace()
                    // TODO: Implement onFailure block for PATCH /items/:id/restore
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    patch("/items/:id/move").handler { context ->
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            val itemId = UUID.fromString(context.pathParam("id"))
            itemsService.moveItem(
                id = itemId,
                parentId = json.getValue("parent_id").let {
                    if (it is String && it.trim().isNotEmpty()) {
                        UUID.fromString(it)
                    } else {
                        null
                    }
                },
                userId = user.id
            )
                .onSuccess {
                    syncEventsService.createEvent(
                        itemId = itemId,
                        eventType = SyncEventType.MOVE,
                        bitMask = user.deviceBitMask,
                        userId = user.id,
                    ).onComplete {
                        context.success()
                    }
                }
                .onFailure { throwable ->
                    // TODO: Implement onFailure block for PATCH /items/:id/move
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }


    delete("/items/:id").handler { context ->
        context.withAuth(authService) { user ->
            val itemId = UUID.fromString(context.pathParam("id"))
            itemsService.softDeleteItem(
                id = itemId,
                userId = user.id
            ).onSuccess {
                syncEventsService.createEvent(
                    itemId = itemId,
                    eventType = SyncEventType.SOFT_DELETE,
                    bitMask = user.deviceBitMask,
                    userId = user.id,
                ).onComplete { d , t ->
                    context.success()
                }
            }
                .onFailure { throwable ->
                    // TODO: Implement onFailure block for DELETE /items/:id
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    delete("/items/:id/permanent").handler { context ->
        context.withAuth(authService) { user ->
            val itemId = UUID.fromString(context.pathParam("id"))
            itemsService.deleteItem(
                id = itemId,
                userId = user.id
            ).onSuccess {
                syncEventsService.createEvent(
                    itemId = itemId,
                    eventType = SyncEventType.DELETE,
                    bitMask = user.deviceBitMask,
                    userId = user.id,
                ).onComplete { d, t->
                    t.printStackTrace()
                    context.success()
                }
            }
                .onFailure { throwable ->
                    // TODO: Implement onFailure block for DELETE /items/:id/permanent
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}