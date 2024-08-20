package com.automattic.android.experimentation.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class AssignmentsDto(
    @Json(name = "variations")
    val variations: Map<String, String?>,
    @Json(name = "ttl")
    val ttl: Int
)
