package com.glimmer.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FEAT-08: the SMS/WhatsApp body used to be the single fixed string "Happy Birthday {name}! 🎂🎉"
 * everywhere. [default] is that same starting point, still editable; [perRelationship] lets a
 * message to your manager read differently from one to your sibling — bounded to the five preset
 * relationship categories (Add/Edit still surface those as quick-pick chips even though the field
 * itself is free text now) rather than an unbounded per-person or per-custom-relationship map,
 * which would need UI that scales with however many relationships exist.
 */
@Serializable
data class WishTemplates(
    val default: String = DEFAULT_TEMPLATE,
    val perRelationship: Map<String, String> = emptyMap()
) {
    fun resolve(relationship: String): String = perRelationship[relationship]?.takeIf { it.isNotBlank() } ?: default

    // Explicit reified type argument — Json.encodeToString(this) alone lets the compiler resolve
    // to the (serializer, value) two-arg overload instead of the reified one-arg extension,
    // treating `this` as a SerializationStrategy and complaining there's no `value` supplied.
    fun toJson(): String = Json.encodeToString<WishTemplates>(this)

    companion object {
        const val DEFAULT_TEMPLATE = "Happy Birthday {name}! 🎂🎉"

        fun fromJson(json: String?): WishTemplates {
            if (json.isNullOrBlank()) return WishTemplates()
            return try {
                Json.decodeFromString(json)
            } catch (t: Throwable) {
                GLog.w("WishTemplate", "Failed to parse stored wish templates; using defaults", t)
                WishTemplates()
            }
        }
    }
}

/**
 * Fills in a template's placeholders. `{age}` becomes empty (not "null") when the birth year
 * isn't known (FEAT-05) — a template using it just reads a little oddly rather than breaking.
 */
fun renderWishTemplate(template: String, name: String, age: Int?, relationship: String): String =
    template
        .replace("{name}", name)
        .replace("{age}", age?.toString() ?: "")
        .replace("{relationship}", relationship)
