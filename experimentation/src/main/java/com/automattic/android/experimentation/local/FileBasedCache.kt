package com.automattic.android.experimentation.local

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.remote.AssignmentsDtoJsonAdapter
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toAssignments
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class FileBasedCache(
    private val cacheDir: File,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val jsonAdapter: AssignmentsDtoJsonAdapter = AssignmentsDtoJsonAdapter(moshi),
) {

    private val assignmentsFile = File(cacheDir, "assignments.json")

    suspend fun getAssignments(): Assignments? {
        return withContext(Dispatchers.IO) {
            assignmentsFile.takeIf { it.exists() }?.readText()?.let { json ->
                val dto = jsonAdapter.fromJson(json)
                dto?.toAssignments(null)
            }
        }
    }

    suspend fun saveAssignments(assignments: Assignments) {
        withContext(Dispatchers.IO) {
            val dto = assignments.toDto()
            val json = jsonAdapter.serializeNulls().toJson(dto)

            assignmentsFile.writeText(json)
        }
    }
}
