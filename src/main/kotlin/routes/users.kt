package com.amqhi.routes

import com.amqhi.common.success
import com.amqhi.common.withAuth
import com.amqhi.services.AuthService
import com.amqhi.services.UsersService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router

fun Router.mountUsersRouter(authService: AuthService, usersService: UsersService) {
    post("/users").handler { context ->
        val body = context.body().asJsonObject()
        usersService.createdUser(
            email = body.getString("email"),
            name = body.getString("name"),
            rawPassword = body.getString("password")
        ).onSuccess {
            context.success()
        }.onFailure { throwable ->
            throwable.printStackTrace()
            // TODO: Implement onFailure block for POST /users
            context.response().putHeader("content-type", "text/plain")
                .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
        }
    }

    get("/users/me").handler { context ->
        context.withAuth(authService) { authenticatedUser ->
            usersService.getUserById(authenticatedUser.id)
                .onSuccess { user ->
                    context.response().setStatusCode(HttpResponseStatus.OK.code()).end(JsonObject()
                        .put("id", user.id.toString())
                        .put("email", user.email)
                        .put("name", user.name)
                        .put("login_type", user.loginType?.toString()?.lowercase())
                        .put("created_at", user.createdAt?.toString())
                        .put("updated_at", user.updatedAt?.toString())
                        .put("profile_picture_id", user.profilePictureId?.toString())
                        .toString())
                }
            .onFailure { throwable ->
                // TODO: Implement onFailure block for GET /users/me
                context.response().putHeader("content-type", "text/plain")
                    .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("OMG")
            }
        }
    }
}