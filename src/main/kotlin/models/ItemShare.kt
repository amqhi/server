/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.models

import java.time.OffsetDateTime
import java.util.UUID

data class ItemShare(
    val id: UUID,
    val itemId: UUID,
    val key: String,
    val expiresAt: OffsetDateTime?,
    val viewCount: Int,
    val maxViews: Int?,
    val enabled: Boolean
)