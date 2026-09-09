/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

data class UserDevice(
    val id: UUID,
    val userId: UUID,
    val name: String?,
    val os: OsType?,
    val appType: AppType,
    val bitMask: Int,
    val registeredAt: OffsetDateTime,
    val lastActiveAt: OffsetDateTime
)
