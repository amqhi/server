package com.amqhi.models

import java.time.OffsetDateTime
import java.util.UUID

enum class OsType {
    WINDOWS,
    LINUX,
    MACOS,
    ANDROID,
    IOS,
    IPADOS,
    POSTMARKETOS;
}

enum class DeviceType {
    PHONE,
    TABLET,
    DESKTOP,
    TERMINAL
}

data class UserDevice(
    val id: UUID,
    val userId: UUID,
    val name: String?,
    val os: OsType?,
    val type: DeviceType?,
    val bitMask: Int,
    val registeredAt: OffsetDateTime,
    val lastActiveAt: OffsetDateTime
)
