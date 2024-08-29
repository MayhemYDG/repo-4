package com.automattic.android.experimentation

import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.SystemClock
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExperimentRestClient
import com.automattic.android.experimentation.repository.AssignmentsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

public interface VariationsRepository {
    public fun configure(anonId: String? = null)

    /**
     * This returns the current active [Variation] for the provided [Experiment].
     *
     * If no active [Variation] is found, we can assume this is the first time this method is being
     * called for the provided [Experiment] during the current session. In this case, the [Variation]
     * is returned from the cached [Assignments] and then set as active. If the cached [Assignments]
     * is stale and [shouldRefreshIfStale] is `true`, then new [Assignments] are fetched and their
     * variations are going to be returned by this method on the next session.
     *
     * If the provided [Experiment] was not included in [experiments], then [Control] is returned.
     * If [isDebug] is `true`, an [IllegalArgumentException] is thrown instead.
     */
    public fun getVariation(experiment: Experiment): Variation
    public fun refreshIfNeeded()
    public fun forceRefresh()
    public fun clear()

    public companion object {
        public fun create(
            platform: String,
            experiments: Set<Experiment>,
            logger: ExperimentLogger,
            coroutineScope: CoroutineScope,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            isDebug: Boolean,
            cacheDir: File,
        ): ExPlat {
            val restClient = ExperimentRestClient(dispatcher = dispatcher)
            val cache = FileBasedCache(cacheDir, dispatcher = dispatcher, scope = coroutineScope)
            val assignmentsRepository = AssignmentsRepository(restClient, cache)
            val assignmentsValidator = AssignmentsValidator(SystemClock())
            return ExPlat(
                platform = platform,
                experiments = experiments,
                logger = logger,
                coroutineScope = coroutineScope,
                isDebug = isDebug,
                assignmentsValidator = assignmentsValidator,
                repository = assignmentsRepository,
            )
        }
    }
}
