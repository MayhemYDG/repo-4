package com.automattic.android.experimentation.repository

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExperimentRestClient

internal class AssignmentsRepository(
    private val experimentRestClient: ExperimentRestClient,
    private val cache: FileBasedCache,
) {

    suspend fun fetch(
        platform: String,
        experimentNames: List<String>,
        anonymousId: String? = null,
    ): Result<Assignments> {
        val fetchResult =
            experimentRestClient.fetchAssignments(platform, experimentNames, anonymousId)

        return fetchResult.fold(
            onFailure = {
                Result.failure(it)
            },
            onSuccess = { assignments ->
                cache.saveAssignments(assignments)
                Result.success(assignments)
            },
        )
    }

    suspend fun getCached(): Assignments? {
        return cache.getAssignments()
    }

    suspend fun clear() {
        cache.clear()
    }
}
