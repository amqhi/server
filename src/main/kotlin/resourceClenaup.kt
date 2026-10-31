/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi

import com.amqhi.services.AuthService

fun cleanupObsoleteResources(authService: AuthService) {
    // TODO: log error on delete obsolete tokens failure
    authService.deleteObsoleteTokens().onSuccess {
        // TODO: log error on delete obsolete user devices failure
        authService.deleteObsoleteUserDevices()
    }
}