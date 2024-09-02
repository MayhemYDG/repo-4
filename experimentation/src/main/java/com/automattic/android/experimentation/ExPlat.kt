package com.automattic.android.experimentation

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.repository.AssignmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

public class ExPlat internal constructor(
    private val platform: String,
    experiments: Set<Experiment>,
    private val logger: ExperimentLogger,
    private val coroutineScope: CoroutineScope,
    private val isDebug: Boolean,
    private val assignmentsValidator: AssignmentsValidator,
    private val repository: AssignmentsRepository,
) : VariationsRepository {
    private val activeVariations = mutableMapOf<String, Variation>()
    private val experimentIdentifiers: List<String> = experiments.map { it.identifier }

    private var anonymousId: String? = null

    override fun initialize(anonymousId: String) {
        this.anonymousId = anonymousId
        invalidateCache()
    }

    override fun getVariation(experiment: Experiment): Variation {
        if (anonymousId == null) {
            return guardAgainstNotInitializedSdk()
        }
        val experimentIdentifier = experiment.identifier
        if (!experimentIdentifiers.contains(experimentIdentifier)) {
            val message =
                "ExPlat: experiment not found: \"${experimentIdentifier}\"! Make sure to include it in the set provided via constructor."
            val illegalArgumentException = IllegalArgumentException(message)
            logger.e(message, illegalArgumentException)
            if (isDebug) {
                throw illegalArgumentException
            } else {
                return Control
            }
        }
        return activeVariations.getOrPut(experimentIdentifier) {
            getAssignments()?.variations?.get(experimentIdentifier) ?: Control
        }
    }

    private fun guardAgainstNotInitializedSdk(): Control {
        val message =
            "ExPlat: anonymousId is null, cannot fetch assignments. Make sure ExPlat was initialized."
        val exception = IllegalStateException(message)
        if (isDebug) throw exception
        logger.e(message, exception)
        return Control
    }

    override fun clear() {
        logger.d("ExPlat: clearing cached assignments and active variations")
        activeVariations.clear()
        anonymousId = null
        coroutineScope.launch { repository.clearCache() }
    }

    private fun getAssignments(): Assignments? = repository.getCached()

    private fun invalidateCache(): Assignments? {
        val cachedAssignments: Assignments? = repository.getCached()
        if (cachedAssignments == null ||
            assignmentsValidator.isStale(cachedAssignments) ||
            cachedAssignments.anonymousId != anonymousId
        ) {
            coroutineScope.launch { fetchAssignments() }
        }
        return cachedAssignments
    }

    private suspend fun fetchAssignments() {
        anonymousId?.let { anonymousId ->
            repository.fetch(platform, experimentIdentifiers, anonymousId).fold(
                onSuccess = {
                    logger.d("ExPlat: fetching assignments successful with result: $it")
                },
                onFailure = {
                    logger.d("ExPlat: fetching assignments failed with result: $it")
                },
            )
        }
    }
}
