package com.example.recipebox

import android.net.Uri

object SecureUrl {
    fun validate(raw: String): Result<String> = runCatching {
        val candidate = extractFirstUrl(raw) ?: error("No web link found")
        val uri = Uri.parse(candidate)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || scheme == "http") { "Only http/https recipe links are accepted" }
        require(!uri.host.isNullOrBlank()) { "Invalid web address" }
        require(uri.encodedAuthority?.contains("@") != true) { "Links containing embedded usernames/passwords are not accepted" }
        candidate
    }

    fun extractFirstUrl(text: String): String? = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
        .find(text)?.value?.trimEnd('.', ',', ')', ']', '}')
}
