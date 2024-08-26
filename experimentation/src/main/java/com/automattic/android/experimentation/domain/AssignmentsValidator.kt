package com.automattic.android.experimentation.domain

internal class AssignmentsValidator(private val clock: Clock) {

    private val Assignments.expiresAt
        get() = fetchedAt + timeToLive

    fun isStale(assignments: Assignments): Boolean =
        clock.currentTimeSeconds() > assignments.expiresAt
}
