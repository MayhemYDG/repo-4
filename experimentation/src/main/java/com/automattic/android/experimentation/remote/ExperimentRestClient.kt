package com.automattic.android.experimentation.remote

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toAssignments
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.wordpress.android.fluxc.store.ExperimentStore

internal class ExperimentRestClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().build(),
    private val jsonAdapter: AssignmentsDtoJsonAdapter = AssignmentsDtoJsonAdapter(moshi),
    private val urlBuilder: UrlBuilder = ExPlatUrlBuilder(),
) {

    suspend fun fetchAssignments(
        platform: String,
        experimentNames: List<String>,
        anonymousId: String? = null,
    ): Result<Assignments> {

        val url = urlBuilder.buildUrl(platform, experimentNames, anonymousId)

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure<Assignments>(Exception("Failed to fetch assignments"))
                } else {
                    val fetchAssignmentDto = jsonAdapter.fromJson(response.body!!.source())
                        ?: return@withContext Result.failure<Assignments>(Exception("Failed to parse assignments"))

                    Result.success(
                        fetchAssignmentDto.toAssignments(System.currentTimeMillis())
                    )
                }
            }
        }
    }
}
