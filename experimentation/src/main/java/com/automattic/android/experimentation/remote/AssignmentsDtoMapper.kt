package com.automattic.android.experimentation.remote

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.Variation

internal object AssignmentsDtoMapper {
    private const val CONTROL = "control"
    fun AssignmentsDto.toAssignments(fetchedAt: Long): Assignments {
        return Assignments(
            variations = variations.mapValues { (_, value) ->
                // API returns null for control group, but the FluxC implementation covered case
                // in which API returns "control" String. To be safe, we handle both cases here
                if (value == null || value == CONTROL) {
                    Variation.Control
                } else {
                    Variation.Treatment(value)
                }
            },
            ttl = ttl,
            fetchedAt = fetchedAt,
        )
    }
    fun Assignments.toDto(): Pair<AssignmentsDto, Long> {
        return AssignmentsDto(
            variations = variations.mapValues { (_, value) ->
                when (value) {
                    is Variation.Control -> CONTROL
                    is Variation.Treatment -> value.name
                }
            },
            ttl = ttl,
        ) to fetchedAt
    }
}
