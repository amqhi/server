/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.common

import com.amqhi.models.AuthenticatedUser
import com.amqhi.services.AuthService
import io.vertx.ext.web.RoutingContext
import kotlin.text.startsWith
import kotlin.text.substringAfter

fun RoutingContext.withAuth(authService: AuthService, onAuthorized: (AuthenticatedUser) -> Unit) {
    val authHeader: String? = request().getHeader("Authorization")
    val accessToken = authHeader
        ?.takeIf { it.startsWith("Bearer ") }
        ?.substringAfter("Bearer ")

    if (accessToken == null) {
        response().setStatusCode(401).end("Unauthorized: No token provided")
        return
    }

    authService.authenticate(accessToken)
        .onSuccess { user ->
            onAuthorized(user)
        }
        .onFailure {
            response().setStatusCode(401).end("Unauthorized: Invalid token")
        }
}