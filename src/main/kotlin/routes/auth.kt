package com.amqhi.routes

import com.amqhi.common.notFound
import com.amqhi.services.AuthService
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.Cookie
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Router.mountAuthRouter(authService: AuthService) {
    post("/auth/login").handler { context ->
        val json = context.body().asJsonObject()
        authService.login(
            email = json.getString("email"),
            password = json.getString("password"),
            deviceName = context.request().getHeader("X-Device-Name"),
            deviceType = context.request().getHeader("X-Device-Type")?.uppercase(),
            osType = context.request().getHeader("X-Device-OS")?.uppercase(),
            ipAddress = context.request().remoteAddress().hostAddress()
        ).onSuccess { tokenPair ->
            context.response()
                .setStatusCode(HttpResponseStatus.OK.code()).end(
                    JsonObject()
                        .put("access_token", tokenPair.accessToken.value)
                        .put("refresh_token", tokenPair.refreshToken.value)
                        .put("token_type", "Bearer")
                        .put("expires_in", tokenPair.accessToken.expiresIn)
                        .toString()
                )
        }
            .onFailure {
                // TODO: Handle specific exceptions (e.g., AuthException -> 401, Throwable -> 500)
                context.response().setStatusCode(401).end("Authentication failed")
            }
    }
    post("/auth/refresh").handler(BodyHandler.create()).handler { context ->
        val body = context.body().asJsonObject()
        val refreshToken = body.getString("refresh_token")
        authService.refreshToken(
            refreshToken = refreshToken,
            ipAddress = context.request().remoteAddress().hostAddress()
        )
            .onSuccess { tokenPair ->
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(tokenPair.toResponse())
            }
            .onFailure {
                // TODO: Handle specific exceptions (e.g., AuthException -> 401, Throwable -> 500)
                context.response().setStatusCode(401).end("Authentication failed")
            }
    }

    post("/auth/google").handler { context ->
        val body = context.body().asJsonObject()
        val idToken = body.getString("id_token")
        authService.exchangeGoogleToken(
            idToken = idToken,
            deviceName = context.request().getHeader("X-Device-Name"),
            deviceType = context.request().getHeader("X-Device-Type")?.uppercase(),
            osType = context.request().getHeader("X-Device-OS")?.uppercase(),
            ipAddress = context.request().remoteAddress().hostAddress()
        ).onSuccess { tokenPair ->
            context.response().putHeader("Content-Type", "application/json").end(tokenPair.toResponse())
        }.onFailure {
            // TODO: Handle specific exceptions (e.g., AuthException -> 401, Throwable -> 500)
            context.response().setStatusCode(401).end("Authentication failed")
        }
    }

    get("/auth/google/callback").handler { context ->
        val code: String? = context.request().getParam("code")
        val rawState: String? = context.request().getParam("state")
        try {

            val decodedState: String = URLDecoder.decode(rawState, StandardCharsets.UTF_8)
            val state = JsonObject(decodedState)

            if (code == null) {
                context.response().setStatusCode(400).end("Authorization code not found")
                return@handler
            }

            authService.googleCallback(
                code = code,
                state = state,
                deviceName = context.request().getHeader("X-Device-Name"),
                deviceType = context.request().getHeader("X-Device-Type"),
                osType = context.request().getHeader("X-Device-OS"),
                ipAddress = context.request().remoteAddress().hostAddress()
            ).onSuccess { tokenPair ->
                context.response()
                    .addCookie(
                        Cookie.cookie("aq_rt_${state.getString("session")}", tokenPair.refreshToken.value)
                            .setPath("/")
                            .setMaxAge(2592000)
                    )
                    // TODO: Replace with configurable web client url
                    .putHeader("Location", "http://localhost:5173")
                    .setStatusCode(302)
                    .end()
            }
                .onFailure {
                    it.printStackTrace()
                    println(it.message)
                    context.notFound()
                }
        } catch (e: Exception) {
            // TODO: Handle specific exceptions (e.g., AuthException -> 401, Throwable -> 500)
            context.response().setStatusCode(401).end("Authentication failed")
        }
    }
}