package com.automattic.android.experimentation.remote

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.Clock
import com.automattic.android.experimentation.domain.SystemClock
import com.automattic.android.experimentation.remote.AssignmentsDtoMapper.toAssignments
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class ExperimentRestClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().build(),
    private val jsonAdapter: AssignmentsDtoJsonAdapter = AssignmentsDtoJsonAdapter(moshi),
    private val urlBuilder: UrlBuilder = ExPlatUrlBuilder(),
    private val clock: Clock = SystemClock(),
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
                    Result.failure<Assignments>(IOException("Unexpected code $response"))
                } else {
                    val fetchAssignmentDto = jsonAdapter.fromJson(response.body!!.source())
                        ?: return@withContext Result.failure<Assignments>(IOException("Failed to parse assignments"))

                    Result.success(
                        fetchAssignmentDto.toAssignments(clock.currentTimeMillis())
                    )
                }
            }
        }
    }
}
