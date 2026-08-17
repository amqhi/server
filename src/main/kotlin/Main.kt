package com.amqhi

import com.amqhi.common.LogConfig
import com.amqhi.common.DatabaseProvider
import com.amqhi.routes.mountAuthRouter
import com.amqhi.routes.mountFilesRouter
import com.amqhi.routes.mountFoldersRouter
import com.amqhi.routes.mountItemsRouter
import com.amqhi.routes.mountNotesRouter
import com.amqhi.routes.mountSyncRouter
import com.amqhi.routes.mountUsersRouter
import com.amqhi.services.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.internal.logging.Logger
import io.vertx.core.internal.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.PoolOptions

const val VERSION = "0.0.0"
val logger: Logger? = LoggerFactory.getLogger(App::class.java)

class App : AbstractVerticle() {

    override fun start() {
        val pgConnectOptions: PgConnectOptions = PgConnectOptions()
            .setPort(System.getenv("DB_PORT").toInt())
            .setHost(System.getenv("DB_HOST"))
            .setDatabase(System.getenv("DB_NAME"))
            .setUser(System.getenv("DB_USER"))
            .setPassword(System.getenv("DB_PASSWORD"))

        val pgPoolOptions: PoolOptions = PoolOptions().setMaxSize(5)
        val databaseProvider = DatabaseProvider(vertx = vertx, connectOptions = pgConnectOptions, poolOptions = pgPoolOptions)
        val webClientService = WebClientService(vertx)
        val storageWorkerPool = vertx.createSharedWorkerExecutor("storage-worker-pool", System.getenv("STORAGE_WORKER_POOL_SIZE")?.toInt() ?: 20)
        val ffmpegWorkerPool = vertx.createSharedWorkerExecutor(
            "ffmpeg-worker-pool",
            System.getenv("FFMPEG_WORKER_POOL_SIZE")?.toInt() ?: Runtime.getRuntime().availableProcessors()
        )
        val passwordWorkerPool = vertx.createSharedWorkerExecutor(
            "password-worker-pool",
            System.getenv("PASSWORD_WORKER_POOL_SIZE")?.toInt() ?: 3
        )

        val authService = AuthService(vertx, databaseProvider.pool, webClientService, passwordWorkerPool)
        val syncEventsService = SyncEventsService(databaseProvider.pool)

        val storageService = StorageService(
            workerExecutor = storageWorkerPool
        )
        val fileProcessingService = FileProcessingService(
            fileSystem = vertx.fileSystem(),
            workerExecutor = ffmpegWorkerPool
        )

        val router = Router.router(vertx)

        router.route().handler(
            CorsHandler.create()
            .addOrigin("http://localhost:5173")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowedHeader("Authorization")
            .allowedHeader("Content-Type")
            .allowCredentials(true)
        )
        router.route().handler(BodyHandler.create())
        router.route("/version").handler { context ->
            context.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end(VERSION)
        }
        router.mountAuthRouter(
            authService
        )
        router.mountItemsRouter(
            authService,
            syncEventsService,
            ItemsService(
                databaseProvider.pool,
                storageService,
                fileProcessingService
            )
        )
        router.mountFilesRouter(
                authService,
            syncEventsService,
                FilesService(
                    databaseProvider.pool,
                    storageService
                )
        )
        router.mountFoldersRouter(
            authService,
            FoldersService(
                databaseProvider.pool
            )
        )
        router.mountNotesRouter(
            authService,
            NotesService(
                databaseProvider.pool
            )
        )

        router.mountSyncRouter(
            authService,
            SyncEventsService(databaseProvider.pool)
        )

        router.mountUsersRouter(
            authService,
            UsersService(
                databaseProvider.pool,
                passwordWorkerPool
            )
        )

        // TODO: Delete obsolete tokens
        databaseProvider.pool.preparedQuery("DELETE FROM tokens WHERE revoked_at IS NOT NULL").execute()

        vertx.createHttpServer().requestHandler(router).listen(System.getenv("PORT")?.toInt() ?: 8000).onSuccess {
            println("listening on http://localhost:${System.getenv("PORT")?.toInt() ?: 8000}")
        }.onFailure {
            println("failed")
            println(it.message)
        }
    }
}

fun main() {

    LogConfig.setup()

    val vertx = Vertx.vertx(
        VertxOptions()
            .setPreferNativeTransport(true)
            .setEventLoopPoolSize(System.getenv("EVENT_LOOP_POOL_SIZE")?.toInt() ?: Runtime.getRuntime().availableProcessors())
    )

    vertx.deployVerticle(App())
}