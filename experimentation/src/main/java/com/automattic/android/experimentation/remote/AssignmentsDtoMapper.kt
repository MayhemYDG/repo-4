package com.automattic.android.experimentation.remote

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.local.CacheDto

internal object AssignmentsDtoMapper {
    private const val CONTROL = "control"
    fun AssignmentsDto.toAssignments(fetchedAt: Long, anonymousId: String): Assignments {
        return Assignments(
            variations = variations.mapValues { (_, value) ->
                // API returns null for control group, but a previous implementation (back from FluxC)
                // covered a case in which API returns "control" String. To be safe, we handle both cases.
                if (value == null || value == CONTROL) {
                    Variation.Control
                } else {
                    Variation.Treatment(value)
                }
            },
            timeToLive = ttl,
            fetchedAt = fetchedAt,
            anonymousId = anonymousId,
        )
    }
    fun Assignments.toDto(): CacheDto {
        return CacheDto(
            assignmentsDto = AssignmentsDto(
                variations = variations.mapValues { (_, value) ->
                    when (value) {
                        is Variation.Control -> CONTROL
                        is Variation.Treatment -> value.name
                    }
                },
                ttl = timeToLive,
            ),
            fetchedAt = fetchedAt,
            anonymousId = anonymousId,
        )
    }
}
