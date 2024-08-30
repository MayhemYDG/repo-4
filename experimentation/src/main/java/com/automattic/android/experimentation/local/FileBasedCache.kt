package com.automattic.android.experimentation.local

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.remote.AssignmentsDtoJsonAdapter
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toAssignments
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class FileBasedCache(
    cacheDir: File,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val jsonAdapter: AssignmentsDtoJsonAdapter = AssignmentsDtoJsonAdapter(moshi),
    private val dispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
) {

    private val assignmentsFile = File(cacheDir, "assignments.json")
    private val type = Types.newParameterizedType(
        Map::class.java,
        Long::class.javaObjectType,
        String::class.java,
    )
    private val wrapperAdapter = moshi.adapter<Map<Long, String>>(type)
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
            assignmentsFile.takeIf { it.exists() }?.readText()?.let { json ->
                val fromJson = wrapperAdapter.fromJson(json) ?: return@let null

                val (fetchedAt, dtoJson) = fromJson.toList().first()

                val dto = jsonAdapter.fromJson(dtoJson)

                dto?.toAssignments(fetchedAt)
            }
        }
    }

    suspend fun saveAssignments(assignments: Assignments) {
        withContext(dispatcher) {
            val (dto, fetchedAt) = assignments.toDto()
            val dtoJson = jsonAdapter.serializeNulls().toJson(dto)

            val wrapperJson = wrapperAdapter.toJson(mapOf(fetchedAt to dtoJson))
            assignmentsFile.writeText(wrapperJson)

            latestMutable = assignments
        }
    }

    suspend fun clear() {
        withContext(dispatcher) {
            assignmentsFile.delete()
            latestMutable = null
        }
    }
}
