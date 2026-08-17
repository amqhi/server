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