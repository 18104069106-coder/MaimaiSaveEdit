package com.savetool.maimai.logic

import com.google.gson.JsonElement
import com.google.gson.JsonObject

fun JsonObject?.getIntSafe(key: String, default: Int = 0): Int {
    return this?.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default
}

fun JsonObject?.getLongSafe(key: String, default: Long = 0L): Long {
    return this?.get(key)?.takeIf { !it.isJsonNull }?.asLong ?: default
}

fun JsonObject?.getStringSafe(key: String, default: String = ""): String {
    return this?.get(key)?.takeIf { !it.isJsonNull }?.asString ?: default
}

fun JsonObject?.getBooleanSafe(key: String, default: Boolean = false): Boolean {
    val element = this?.get(key) ?: return default
    if (element.isJsonNull) return default
    return try {
        if (element.isJsonPrimitive) {
            val p = element.asJsonPrimitive
            if (p.isBoolean) p.asBoolean
            else if (p.isNumber) p.asInt != 0
            else p.asString.lowercase() == "true"
        } else {
            element.asBoolean
        }
    } catch (e: Exception) {
        default
    }
}

fun JsonElement?.asIntSafe(default: Int = 0): Int {
    return this?.takeIf { !it.isJsonNull }?.asInt ?: default
}

fun JsonElement?.asStringSafe(default: String = ""): String {
    return this?.takeIf { !it.isJsonNull }?.asString ?: default
}

fun JsonElement?.asLongSafe(default: Long = 0L): Long {
    return this?.takeIf { !it.isJsonNull }?.asLong ?: default
}

fun JsonElement?.asBooleanSafe(default: Boolean = false): Boolean {
    val element = this ?: return default
    if (element.isJsonNull) return default
    return try {
        if (element.isJsonPrimitive) {
            val p = element.asJsonPrimitive
            if (p.isBoolean) p.asBoolean
            else if (p.isNumber) p.asInt != 0
            else p.asString.lowercase() == "true"
        } else {
            element.asBoolean
        }
    } catch (e: Exception) {
        default
    }
}
