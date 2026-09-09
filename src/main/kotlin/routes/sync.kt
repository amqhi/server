/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.routes

import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.services.AuthService
import com.amqhi.services.SyncEventsService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import java.util.UUID

fun Router.mountSyncRouter(authService: AuthService, syncEventsService: SyncEventsService) {

    route("/sync").handler { ctx ->
        val serverRequest = ctx.request()

        val webSocket = serverRequest.toWebSocket()

        webSocket.onSuccess { ws ->
            ws.handler {
                println(it.toString())
            }
        }.onFailure {
            println(it.toString())
        }
    }

    get("/sync/events").handler { context ->
        context.withAuth(authService) { user ->
            syncEventsService.getEvents(user.deviceBitMask, user.id)
                .onSuccess {
                    context.response().setStatusCode(200).end(JsonObject()
                        // TODO: Split events if the payload is too large.
                        .put("has_more", false)
                        .put("events", it.map { event -> event
                            .toJson()})
                        .toString())
                }
                .onFailure {
                    it.printStackTrace()
                    // TODO: Implement onFailure block for GET /sync/events
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    post("/sync/events/acknowledge").handler { context ->
        context.withAuth(authService) { user ->
            // TODO: Handle failure to retrieve item IDs from request
            val itemIds = context.body().asJsonObject().getJsonArray("item_ids").map { UUID.fromString(it.toString()) }.toTypedArray()
            syncEventsService.acknowledgeEvents(
                bitMask = user.deviceBitMask,
                userId = user.id,
                itemIds = itemIds
            ).onSuccess {
                context.success()
            }
                .onFailure {
                    it.printStackTrace()
                    // TODO: Implement onFailure block for POST /sync/events/acknowledge
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}