package com.colman.aroundme.features.profile

import java.util.Locale

fun humanizeUsername(username: String): String {
    val cleaned = username
        .replace("[._]+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
    if (cleaned.isBlank()) return ""

    return cleaned.split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }
}
