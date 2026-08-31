/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.services

import io.vertx.core.Vertx
import io.vertx.core.http.HttpVersion
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import java.net.http.HttpClient

class WebClientService(vertx: Vertx) {
    val client: WebClient = WebClient.create(vertx, WebClientOptions()
        .setUserAgent("Amqhi/1.0.0 (https://amqhi.com; contact@amqhi.com)")
        .setFollowRedirects(true)
        .setKeepAlive(true))

    val fallbackClient: HttpClient = HttpClient.newHttpClient()
}