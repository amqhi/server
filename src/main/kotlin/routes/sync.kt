package com.amqhi.routes

import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.services.AuthService
import com.amqhi.services.SyncEventsService
import io.netty.handler.codec.http.HttpResponseStatus
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
            syncEventsService.getEvents(user.deviceBitMask)
                .onSuccess {
                    context.response().setStatusCode(200).end("[${
                        it.joinToString(",") { event ->
                            event.toJson().toString()
                        }
                    }]")
                }
                .onFailure {
                    // TODO: Implement onFailure block for GET /sync/events
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    post("/sync/events/:id/consume").handler { context ->
        val eventId = context.pathParam("id")
        context.withAuth(authService) { user ->
            syncEventsService.consumeEvent(
                eventId = UUID.fromString(eventId),
                bitMask = user.deviceBitMask,
                userId = user.id
            ).onSuccess {
                context.success()
            }
                .onFailure {
                    it.printStackTrace()
                    // TODO: Implement onFailure block for POST /sync/events/:id/consume
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}