/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.common

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.RoutingContext

fun RoutingContext.notFound() {
    response().putHeader("content-type", "text/plain").setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end(
        Messages.NOT_FOUND)
}

fun RoutingContext.success() {
    response().putHeader("content-type", "text/plain").setStatusCode(HttpResponseStatus.OK.code()).end(Messages.SUCCESS)
}