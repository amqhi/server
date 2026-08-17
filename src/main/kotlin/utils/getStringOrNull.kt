package com.amqhi.utils

import io.vertx.sqlclient.Row

fun Row.getStringOrNull(column: String): String? = if (this.getValue(column) == null) null else this.getString(column)