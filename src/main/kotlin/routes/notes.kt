package com.amqhi.routes

import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.models.ItemAttributes
import com.amqhi.services.AuthService
import com.amqhi.services.NotesService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import java.util.UUID

fun Router.mountNotesRouter(authService: AuthService, notesService: NotesService) {
    post("/notes").handler { context ->
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            notesService.createNote(
                itemAttributes = ItemAttributes.from(user.id, json),
                title = json.getValue("title") as? String,
                subtitle = json.getValue("subtitle") as? String,
                content = json.getJsonArray("content"),
                style = json.getValue("style") as? JsonObject,
                extra = json.getValue("extra") as? JsonObject
            ).onSuccess {
                context.success()
            }
                .onFailure {
                    // TODO: Implement onFailure block for POST /notes
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }

    patch("/notes/:id").handler { context ->
        context.withAuth(authService) { user ->
            val json = context.body().asJsonObject()
            notesService.updateNote(
                id = UUID.fromString(context.pathParam("id")),
                itemAttributes = ItemAttributes.from(user.id, json),
                title = json.getValue("title") as? String,
                subtitle = json.getValue("subtitle") as? String,
                content = json.getValue("content") as? JsonArray,
                style = json.getValue("style") as? JsonObject,
                extra = json.getValue("extra") as? JsonObject
            ).onSuccess {
                context.success()
            }
                .onFailure {
                    // TODO: Implement onFailure block for PATCH /notes/:id
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}
