package com.automattic.android.experimentation.domain

internal data class Assignments(
    val variations: Map<String, Variation>,
    val timeToLive: Int,
    val fetchedAt: Long,
    val anonymousId: String,
)
