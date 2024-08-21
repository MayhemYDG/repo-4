package com.automattic.android.experimentation.remote

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.Variation

internal object AssignmentsDtoMapper {
    fun AssignmentsDto.toAssignments(externalFetchedAt: Long?): Assignments {
        return Assignments(
            variations = variations.mapValues { (key, value) ->
                if (value == null || value == "control") {
                    Variation.Control
                } else {
                    Variation.Treatment(value)
                }
            },
            ttl = ttl,
            fetchedAt = externalFetchedAt ?: this.fetchedAt ?: 0,
        )
    }
    fun Assignments.toDto(): AssignmentsDto {
        return AssignmentsDto(
            variations = variations.mapValues { (_, value) ->
                when (value) {
                    is Variation.Control -> null
                    is Variation.Treatment -> value.name
                }
            },
            ttl = ttl,
            fetchedAt = fetchedAt,
        )
    }
}
