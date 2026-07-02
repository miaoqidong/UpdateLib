package com.mqd.updatelib.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @author quzhuligpt@gmail.com
 * @since 2024/11/24
 */
object JsonHelper {

    val globalJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encodeToString(value: T): String {
        return globalJson.encodeToString(value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return globalJson.decodeFromString(string)
    }
}
