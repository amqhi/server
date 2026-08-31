/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.utils

import io.vertx.sqlclient.Row

fun Row.getStringOrNull(column: String): String? = if (this.getValue(column) == null) null else this.getString(column)