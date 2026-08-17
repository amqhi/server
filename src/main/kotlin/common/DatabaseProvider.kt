package com.amqhi.common

import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions

class DatabaseProvider(vertx: Vertx, connectOptions: PgConnectOptions, poolOptions: PoolOptions) {

    val pool: Pool = PgBuilder
        .pool()
        .with(poolOptions)
        .connectingTo(connectOptions)
        .using(vertx)
        .build()
}