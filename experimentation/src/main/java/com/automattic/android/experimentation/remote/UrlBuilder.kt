package com.automattic.android.experimentation.remote

import okhttp3.HttpUrl

fun interface UrlBuilder {
    fun buildUrl(
        platform: String,
        experimentNames: List<String>,
        anonymousId: String?,
    ): HttpUrl
}

class ExPlatUrlBuilder : UrlBuilder {
    override fun buildUrl(
        platform: String,
        experimentNames: List<String>,
        anonymousId: String?,
    ): HttpUrl {
        return HttpUrl.Builder()
            .scheme("https")
            .host("public-api.wordpress.com")
            .addPathSegment("wpcom")
            .addPathSegment("v2")
            .addPathSegment("experiments")
            .addPathSegment("0.1.0")
            .addPathSegment("assignments")
            .addPathSegment(platform)
            .apply {
                experimentNames.forEach { addQueryParameter("experiment_names", it) }
                addQueryParameter("anon_id", anonymousId.orEmpty())
            }
            .build()
    }
}
