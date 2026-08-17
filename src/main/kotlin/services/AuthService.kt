package com.amqhi.services

import com.amqhi.models.AccessToken
import com.amqhi.models.AuthToken
import com.amqhi.models.AuthenticatedUser
import com.amqhi.models.DeviceType
import com.amqhi.models.LoginType
import com.amqhi.models.OsType
import com.amqhi.models.TokenPair
import com.amqhi.models.User
import com.amqhi.models.UserDevice
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.password4j.Password
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.WorkerExecutor
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.authentication.TokenCredentials
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.auth.oauth2.providers.GoogleAuth
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

class AuthService(
    vertx: Vertx,
    private val pool: Pool,
    private val webClientService: WebClientService,
    private val workerExecutor: WorkerExecutor,
    private val googleClientIdWeb: String = System.getenv("GOOGLE_CLIENT_ID_WEB"),
    private val googleClientSecretWeb: String = System.getenv("GOOGLE_CLIENT_SECRET_WEB"),
    googleClientIdAndroid: String = System.getenv("GOOGLE_CLIENT_ID_ANDROID"),
    googleClientIdIOS: String = System.getenv("GOOGLE_CLIENT_ID_IOS")
) {

    private val config = JWTAuthOptions()
        .setPubSecKeys(
            listOf(
                PubSecKeyOptions()
                    .setAlgorithm("HS256")
                    .setBuffer(System.getenv("JWT_SECRET_KEY"))
            )
        )
    private val jwtAuth: JWTAuth = JWTAuth.create(vertx, config)

    private val googleVerifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(),
        GsonFactory()
    )
        .setAudience(listOf(googleClientIdIOS, googleClientIdAndroid))
        .build()
    var oauth2: OAuth2Auth? = null

    fun authenticate(accessToken: String): Future<AuthenticatedUser> {
        return jwtAuth.authenticate(TokenCredentials(accessToken)).map {
            AuthenticatedUser(
                id = UUID.fromString(it.get("sub")),
                email = it.get("email"),
                name = it.get("name"),
                deviceId = UUID.fromString(it.get("device_id")),
                deviceBitMask = it.get("device_bit_mask"),
            )
        }
    }

    fun init(vertx: Vertx) {
        GoogleAuth.discover(
            vertx,
            OAuth2Options()
                .setClientId(googleClientIdWeb)
                .setClientSecret(googleClientSecretWeb)
                .setSite("https://accounts.google.com")
                .setTokenPath("https://www.googleapis.com/oauth2/v3/token")
                .setAuthorizationPath("/o/oauth2/auth"),
        )
            .onSuccess {
            println("Successfully initialized Google OAuth API")
            oauth2 = it
        }.onFailure {
            println("Failed to initialize Google OAuth API")
            println( it.message )
        }
    }

    fun login(email: String, password: String, deviceName: String?, ipAddress: String?, deviceType: String?, osType: String?): Future<TokenPair> {
        return pool.preparedQuery(
            """
            SELECT password, id, name FROM "users" WHERE email = $1 AND "login_type" = $2
        """.trimIndent()
        )
            .execute(Tuple.of(email, LoginType.NORMAL.toString().lowercase()))
            .compose { rows ->
                if (!rows.any()) {
                    // TODO: Use domain specific exception
                    return@compose Future.failedFuture(NoSuchElementException())
                }

                val row = rows.first()
                val userId = row.getUUID("id")

                workerExecutor.executeBlocking {
                    val matches = Password.check(password, row.getString("password")).withArgon2()
                    if (!matches) {
                        throw NoSuchElementException()
                    }
                }
                    .compose {
                        generatedUserDevice(
                            userId = userId,
                            name = deviceName,
                            osType = osType?.let{type -> OsType.valueOf(type)},
                            type = deviceType?.let{type -> DeviceType.valueOf(type)},
                        )
                    }
                    .compose { userDevice ->
                        generatedRefreshToken(userId, ipAddress, userDevice.id).map { token ->
                            TokenPair(
                                refreshToken = token,
                                accessToken = generatedAccessToken(
                                    userId = userId,
                                    email = email,
                                    name = row.getString("name"),
                                    refreshTokenId = token.id.toString(),
                                    deviceId = userDevice.id,
                                    deviceBitMask = userDevice.bitMask
                                )
                            )
                        }
                    }
            }

    }

//    fun checkPassword(inputPassword: String, storedHash: String): Boolean {
//        val pepper = System.getenv("AMQHI_PEPPER") ?: "default_secret_pepper"
//
//        return Password.check(inputPassword, storedHash)
//            .addPepper(pepper)
//            .withArgon2()
//    }

    //        val argon2: Argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
//
//        val hashedPassword = argon2.hash(12, 65536, 4, password.toCharArray())
//        argon2.wipeArray(password.toCharArray())
//
//        return pool.preparedQuery("INSERT INTO users (id, name, password) VALUES (?, ?, ?)")
//            .execute(Tuple.of(id, name, hashedPassword))
//            .mapEmpty()

    fun generatedUserDevice(userId: UUID, name: String?, osType: OsType?, type: DeviceType?) : Future<UserDevice> {
        return pool.preparedQuery("""
                    WITH next_bit AS (
                    SELECT 
                        COALESCE(MAX(bit_mask) * 2, 1) AS new_mask
                    FROM user_devices 
                    WHERE user_id = $1
                    )
                    INSERT INTO user_devices (
                        user_id, 
                        name, 
                        os, 
                        type, 
                        bit_mask, 
                        registered_at, 
                        last_active_at
                    )
                    SELECT 
                        $1,
                        $2,
                        $3,
                        $4,
                        nb.new_mask,
                        NOW(), 
                        NOW()
                    FROM next_bit nb
                    RETURNING *;
        """.trimIndent())
            .execute(
                Tuple.of(userId, name, osType?.toString()?.lowercase(), type?.toString()?.lowercase())
            )
            .map {
                val row = it.first()
                UserDevice(
                    id = row.getUUID("id"),
                    userId = row.getUUID("user_id"),
                    name = row.getValue("name") as? String,
                    os = (row.getValue("os") as? String)?.let { data -> OsType.valueOf(data.uppercase()) },
                    type = (row.getValue("type") as? String)?.let { data -> DeviceType.valueOf(data.uppercase()) },
                    bitMask = row.getInteger("bit_mask"),
                    registeredAt = row.getOffsetDateTime("registered_at"),
                    lastActiveAt = row.getOffsetDateTime("last_active_at"),
                )
            }
    }

    fun generatedRefreshToken(userId: UUID, ipAddress: String?, deviceId: UUID): Future<AuthToken> {
        val rawToken = ByteArray(32).let {
            SecureRandom().nextBytes(it)
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        val hashedToken = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val expiresAt = OffsetDateTime.now().plusDays(30)

        val query = """
        INSERT INTO tokens (user_id, value, expires_at, created_at, device_id, ip_address)
        VALUES ($1, $2, $3, NOW(), $4, $5)
        RETURNING "id"
    """.trimIndent()
        return pool.preparedQuery(query)
            .execute(Tuple.of(userId, hashedToken, expiresAt,deviceId, ipAddress))
            .map {
                AuthToken(
                    id = it.first().getUUID("id"),
                    userId = userId,
                    value = rawToken,
                    expiresAt = expiresAt,
                    ipAddress = ipAddress,
                    deviceId = deviceId
                )
            }
    }

    fun generatedAccessToken(userId: UUID, email: String, name: String, refreshTokenId: String, deviceId: UUID, deviceBitMask: Int): AccessToken {
        val claims = JsonObject()
            .put("sub", userId.toString())
            .put("email", email)
            .put("name", name)
            .put("jti", refreshTokenId)
            .put("device_id", deviceId.toString())
            .put("device_bit_mask", deviceBitMask)

        val token = jwtAuth.generateToken(
            claims,
            JWTOptions()
                .setExpiresInSeconds(3600)
                .setIssuer("Amqhi")
        )
        return AccessToken(
            token,
            3600
        )
    }

    fun refreshToken(refreshToken: String, ipAddress: String?): Future<TokenPair> {
        val hashedToken = MessageDigest.getInstance("SHA-256")
            .digest(refreshToken.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return pool.preparedQuery(
            """
            SELECT 
                t.user_id,
                t.device_id,
                d.bit_mask
            FROM "tokens" t
            JOIN "user_devices" d 
              ON d.id = t.device_id 
             AND d.user_id = t.user_id
            WHERE t.value = $1 
              AND t.expires_at > NOW() 
              AND t.revoked_at IS NULL;
        """.trimIndent()
        ).execute(Tuple.of(hashedToken)).compose { rows ->
            if (!rows.any()) {
                // TODO: Use domain specific exception
                return@compose Future.failedFuture(Exception())
            }
            val deviceBitMask = rows.first().getInteger("bit_mask")
            val deviceId = rows.first().getUUID("device_id")
            val userId = rows.first().getUUID("user_id")
            pool.preparedQuery(
                """
            UPDATE "tokens" 
            SET revoked_at = NOW() 
            WHERE value = $1 AND user_id = $2 AND revoked_at IS NULL
            RETURNING "id"
        """.trimIndent()
            )
                .execute(Tuple.of(hashedToken, userId))
                .compose { rows ->
                    if (!rows.any()) {
                        // TODO: Use domain specific exception
                        throw NoSuchElementException()
                    }

                    generatedRefreshToken(
                        userId = userId,
                        deviceId = deviceId,
                        ipAddress = ipAddress
                    )
                }.compose { refreshToken ->
                    pool.preparedQuery(
                        """
            SELECT id, email, name FROM "users" WHERE id = $1
        """.trimIndent()
                    ).execute(Tuple.of(userId))
                        .map { rows ->
                            if (!rows.any()) {
                                // TODO: Use domain specific exception
                                throw NoSuchElementException()
                            }
                            val row = rows.first()
                            TokenPair(
                                refreshToken = refreshToken,
                                accessToken = generatedAccessToken(
                                    userId = row.getUUID("id"),
                                    name = row.getString("name"),
                                    email = row.getString("email"),
                                    refreshTokenId = refreshToken.id.toString(),
                                    deviceId = deviceId,
                                    deviceBitMask = deviceBitMask
                                )
                            )
                        }
                }
        }
    }

    fun getOrCreateUser(email: String, providerId: String, name: String, loginType: LoginType): Future<User> {
        return pool.preparedQuery(
            """
        INSERT INTO users (email, provider_id, name, created_at, login_type)
        VALUES ($1, $2, $3, NOW(), $4)
        ON CONFLICT (provider_id) DO NOTHING
    """
        ).execute(Tuple.of(email, providerId, name, loginType.toString().lowercase()))
            .flatMap {
                pool.preparedQuery("SELECT * FROM users WHERE provider_id = $1")
                    .execute(Tuple.of(providerId))
            }
            .map { rows ->
                val row = rows.first()
                User.from(row)
            }
    }


    fun exchangeGoogleToken(idToken: String, deviceName: String?, ipAddress: String?, osType: String?, deviceType: String?): Future<TokenPair> {
        return workerExecutor.executeBlocking {
            googleVerifier.verify(idToken)
        }
            .compose { googleIdToken ->
                val payload = googleIdToken.payload
                getOrCreateUser(
                    email = payload.email,
                    providerId = payload.subject,
                    name = payload["name"] as? String ?: "",
                    loginType = LoginType.GOOGLE
                )
            }
            .compose { user ->
                generatedUserDevice(
                    userId = user.id,
                    name = deviceName,
                    osType = osType?.let { OsType.valueOf(it) },
                    type = deviceType?.let { DeviceType.valueOf(it) },
                )
                    .compose { device ->
                        generatedRefreshToken(
                            userId = user.id,
                            deviceId = device.id,
                            ipAddress = ipAddress
                        )
                            .map {
                                    authToken ->
                                TokenPair(
                                    accessToken = generatedAccessToken(
                                        userId = user.id,
                                        email = user.email,
                                        name = user.name,
                                        refreshTokenId = authToken.id.toString(),
                                        deviceId = device.id,
                                        deviceBitMask = device.bitMask
                                    ),
                                    refreshToken = authToken
                                )
                            }
                    }
            }

    }

    // TODO: Replace with Vert.x Google Auth provider
    fun googleCallback(code: String, state: JsonObject, ipAddress: String?, deviceName: String?, deviceType: String?, osType: String?): Future<TokenPair> {

        val rawFormData =
            """code=${code}&client_id=${googleClientIdWeb}&client_secret=${googleClientSecretWeb}&redirect_uri=http://localhost:8000/auth/google/callback&grant_type=authorization_code"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(rawFormData))
            .build()

        val completableFuture = webClientService.fallbackClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())

        return Future.fromCompletionStage(completableFuture).compose { response ->
            val body = JsonObject(response.body())
            val idToken = body.getString("id_token")

            val parts = idToken.split(".")
            if (parts.size == 3) {
                val payload = String(Base64.getUrlDecoder().decode(parts[1]))
                val payloadJson = JsonObject(payload)

                val googleUserId = payloadJson.getString("sub")
                val email = payloadJson.getString("email")
                getOrCreateUser(
                    email = email,
                    providerId = googleUserId,
                    name = payloadJson.getString("name"),
                    loginType = LoginType.GOOGLE
                ).compose { user ->
                    generatedUserDevice(
                        userId = user.id,
                        name = deviceName,
                        osType = osType?.let { OsType.valueOf(it) },
                        type = deviceType?.let { DeviceType.valueOf(it) },
                    )
                        .compose { userDevice ->
                            generatedRefreshToken(user.id, ipAddress, userDevice.id).map { token ->
                                TokenPair(
                                    refreshToken = token,
                                    accessToken = generatedAccessToken(
                                        userId = user.id,
                                        email = user.email,
                                        name = user.name,
                                        refreshTokenId = token.id.toString(),
                                        deviceId = userDevice.id,
                                        deviceBitMask = userDevice.bitMask
                                    )
                                )
                            }
                        }
                }
            } else {
                return@compose Future.failedFuture(Exception())
            }
        }
    }
}