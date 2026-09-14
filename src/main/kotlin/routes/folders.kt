/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.routes

import com.amqhi.common.withAuth
import com.amqhi.models.ItemAttributes
import com.amqhi.models.ItemType
import com.amqhi.models.SyncEventType
import com.amqhi.services.AuthService
import com.amqhi.services.FoldersService
import com.amqhi.services.SyncEventsService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.Router

fun Router.mountFoldersRouter(authService: AuthService, foldersService: FoldersService, syncEventsService: SyncEventsService) {
    post("/folders").handler { context ->
        context.withAuth(authService) { user ->
            val body = context.body().asJsonObject()
            foldersService.createFolder(
                userId = user.id,
                itemAttributes = ItemAttributes.from(body)
            ).onSuccess { folder ->
                syncEventsService.createEvent(
                    itemId = folder.id,
                    eventType = SyncEventType.CREATE,
                    bitMask = user.deviceBitMask,
                    userId = user.id,
                    itemType = ItemType.FOLDER
                ).onComplete {
                    context.response().setStatusCode(HttpResponseStatus.CREATED.code()).end(folder.toSummaryJson().toString())
                }
            }
                .onFailure {
                    // TODO: Implement onFailure block for POST /folders
                    context.response().putHeader("content-type", "text/plain")
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
                }
        }
    }
}