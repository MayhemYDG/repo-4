package com.automattic.android.experimentation.domain

data class Assignments(
    val variations: Map<String, Variation>,
    val ttl: Int,
    val fetchedAt: Long
)
