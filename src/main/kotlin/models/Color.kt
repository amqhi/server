/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.models

data class Color(
    val value: Int
) {
    val alpha: Int get() = (value ushr 24) and 0xFF
    val red: Int get() = (value ushr 16) and 0xFF
    val green: Int get() = (value ushr 8) and 0xFF
    val blue: Int get() = value and 0xFF

    companion object {
        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Color {
            val value =
                (alpha shl 24) or
                        (red shl 16) or
                        (green shl 8) or
                        blue

            return Color(value)
        }

        fun fromString(value: String): Color {
            val hex = value.removePrefix("#")
            return Color(hex.toLong(16).toInt())
        }
    }

    override fun toString(): String {
        return "#${value.toHexString()}"
    }
}