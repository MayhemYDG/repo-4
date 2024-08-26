package com.automattic.android.experimentation

import com.automattic.android.experimentation.ExPlat.RefreshStrategy.ALWAYS
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.IF_STALE
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.NEVER
import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExperimentRestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.wordpress.android.fluxc.store.ExperimentStore.Platform
import org.wordpress.android.fluxc.utils.AppLogWrapper
import org.wordpress.android.util.AppLog.T

class ExPlat internal constructor(
    private val platform: Platform,
    private val experiments: Set<Experiment>,
    private val appLogWrapper: AppLogWrapper,
    private val coroutineScope: CoroutineScope,
    private val isDebug: Boolean,
    private val cache: FileBasedCache,
    private val assignmentsValidator: AssignmentsValidator,
    private val restClient: ExperimentRestClient,
) {
    private val activeVariations = mutableMapOf<String, Variation>()
    private val experimentIdentifiers: List<String> = experiments.map { it.identifier }

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
            appLogWrapper.e(T.API, message)
            if (isDebug) throw IllegalArgumentException(message) else return Control
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
        appLogWrapper.d(T.API, "ExPlat: clearing cached assignments and active variations")
        activeVariations.clear()
        coroutineScope.launch {
            cache.clear()
        }
    }

    private fun refresh(refreshStrategy: RefreshStrategy) {
        if (experimentIdentifiers.isNotEmpty()) {
            getAssignments(refreshStrategy)
        }
    }

    private fun getAssignments(refreshStrategy: RefreshStrategy): Assignments {
        val cachedAssignments: Assignments = runBlocking { cache.getAssignments() } ?: Assignments(emptyMap(), 0, 0)
        if (refreshStrategy == ALWAYS || (refreshStrategy == IF_STALE && assignmentsValidator.isStale(cachedAssignments))) {
            coroutineScope.launch { fetchAssignments() }
        }
        return cachedAssignments
    }

    private suspend fun fetchAssignments() = restClient.fetchAssignments(platform.value, experimentIdentifiers).fold(
        onSuccess = {
            appLogWrapper.d(T.API, "ExPlat: fetching assignments successful with result: $it")
        },
        onFailure = {
            appLogWrapper.d(T.API, "ExPlat: fetching assignments failed with result: $it")
        },
    )
    private enum class RefreshStrategy { ALWAYS, IF_STALE, NEVER }
}
