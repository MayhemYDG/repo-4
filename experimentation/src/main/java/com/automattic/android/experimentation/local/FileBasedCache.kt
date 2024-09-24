package com.automattic.android.experimentation.local

import com.automattic.android.experimentation.ExperimentLogger
import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toAssignments
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal class FileBasedCache(
    cacheDir: File,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val cacheDtoJsonAdapter: CacheDtoJsonAdapter = CacheDtoJsonAdapter(moshi),
    private val dispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
    private val logger: ExperimentLogger,
    private val failFast: Boolean,
) {

    private val assignmentsFile = File(cacheDir, "assignments.json")
    private var latestMutable: Assignments? = null

    internal val latest: Assignments?
        get() = latestMutable

    init {
        scope.launch {
            withContext(context = dispatcher) {
                latestMutable = getAssignments()
            }
        }
    }

    suspend fun getAssignments(): Assignments? {
        return withContext(dispatcher) {
            assignmentsFile.takeIf { it.exists() }?.readText()?.let { json: String ->
                val fromJson = cacheDtoJsonAdapter.fromJson(json) ?: return@let null

                fromJson.assignmentsDto.toAssignments(
                    fetchedAt = fromJson.fetchedAt,
                    anonymousId = fromJson.anonymousId,
                )
            }
        }
    }

    suspend fun saveAssignments(assignments: Assignments) {
        withContext(dispatcher) {
            val parentFile = assignmentsFile.parentFile
            if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
                val message = "Failed to create parent directories for ${assignmentsFile.path}"
                logger.e(message)
                if (failFast) {
                    throw IOException(message)
                }
            } else {
                val cacheDto: CacheDto = assignments.toDto()
                val dtoJson = cacheDtoJsonAdapter.serializeNulls().toJson(cacheDto)

                assignmentsFile.writeText(dtoJson)

                latestMutable = assignments
            }
        }
    }

    suspend fun clear() {
        withContext(dispatcher) {
            assignmentsFile.delete()
            latestMutable = null
        }
    }
}
