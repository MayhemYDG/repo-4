package com.automattic.android.experimentation

import com.automattic.android.experimentation.ExPlat.RefreshStrategy.ALWAYS
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.IF_STALE
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.NEVER
import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.SystemClock
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExperimentRestClient
import com.automattic.android.experimentation.repository.AssignmentsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ExPlat internal constructor(
    private val platform: String,
    private val experiments: Set<Experiment>,
    private val logger: ExperimentLogger,
    private val coroutineScope: CoroutineScope,
    private val isDebug: Boolean,
    private val assignmentsValidator: AssignmentsValidator,
    private val repository: AssignmentsRepository,
) {
    private val activeVariations = mutableMapOf<String, Variation>()
    private val experimentIdentifiers: List<String> = experiments.map { it.identifier }

    private var anonId: String? = null

    fun configure(anonId: String? = null) {
        clear()
        this.anonId = anonId
    }

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
    fun getVariation(
        experiment: Experiment,
        shouldRefreshIfStale: Boolean = false,
    ): Variation {
        val experimentIdentifier = experiment.identifier
        if (!experimentIdentifiers.contains(experimentIdentifier)) {
            val message = "ExPlat: experiment not found: \"${experimentIdentifier}\"! " +
                "Make sure to include it in the set provided via constructor."
            val illegalArgumentException = IllegalArgumentException(message)
            logger.e(message, illegalArgumentException)
            if (isDebug) {
                throw illegalArgumentException
            } else {
                return Control
            }
        }
        return activeVariations.getOrPut(experimentIdentifier) {
            getAssignments(if (shouldRefreshIfStale) IF_STALE else NEVER)
                .variations[experimentIdentifier] ?: Control
        }
    }

    fun refreshIfNeeded() {
        refresh(refreshStrategy = IF_STALE)
    }

    fun forceRefresh() {
        refresh(refreshStrategy = ALWAYS)
    }

    fun clear() {
        logger.d("ExPlat: clearing cached assignments and active variations")
        activeVariations.clear()
        anonId = null
        coroutineScope.launch {
            repository.clear()
        }
    }

    private fun refresh(refreshStrategy: RefreshStrategy) {
        if (experimentIdentifiers.isNotEmpty()) {
            getAssignments(refreshStrategy)
        }
    }

    private fun getAssignments(refreshStrategy: RefreshStrategy): Assignments {
        val cachedAssignments: Assignments = repository.getCached() ?: Assignments(emptyMap(), 0, -1)
        if (refreshStrategy == ALWAYS || (
                refreshStrategy == IF_STALE && assignmentsValidator.isStale(
                    cachedAssignments,
                )
                )
        ) {
            coroutineScope.launch { fetchAssignments() }
        }
        return cachedAssignments
    }

    private suspend fun fetchAssignments() =
        repository.fetch(platform, experimentIdentifiers, anonId).fold(
            onSuccess = {
                logger.d("ExPlat: fetching assignments successful with result: $it")
            },
            onFailure = {
                logger.d("ExPlat: fetching assignments failed with result: $it")
            },
        )

    private enum class RefreshStrategy { ALWAYS, IF_STALE, NEVER }

    companion object {
        fun create(
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
