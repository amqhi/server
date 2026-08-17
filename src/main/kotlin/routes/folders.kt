package com.amqhi.routes

import com.amqhi.common.withAuth
import com.amqhi.models.ItemAttributes
import com.amqhi.services.AuthService
import com.amqhi.services.FoldersService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.Router

fun Router.mountFoldersRouter(authService: AuthService, foldersService: FoldersService) {
    post("/folders").handler { context ->
        context.withAuth(authService) { user ->
            val body = context.body().asJsonObject()
            foldersService.createFolder(
                itemAttributes = ItemAttributes.from(user.id, body)
            ).onSuccess { folder ->
                context.response().setStatusCode(HttpResponseStatus.CREATED.code()).end(folder.toSummaryJson().toString())
            }
                .onFailure {
                    // TODO: Implement onFailure block for POST /folders
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}