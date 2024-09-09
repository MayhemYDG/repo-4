package com.automattic.android.experimentation.local

import com.automattic.android.experimentation.remote.AssignmentsDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class CacheDto(
    @Json(name = "assignmentsDto")
    val assignmentsDto: AssignmentsDto,
    @Json(name = "fetchedAt")
    val fetchedAt: Long,
    @Json(name = "anonymousId")
    val anonymousId: String,
)
