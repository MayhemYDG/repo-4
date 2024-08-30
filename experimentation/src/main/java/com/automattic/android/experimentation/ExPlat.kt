package com.automattic.android.experimentation

import com.automattic.android.experimentation.ExPlat.RefreshStrategy.ALWAYS
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.IF_STALE
import com.automattic.android.experimentation.ExPlat.RefreshStrategy.NEVER
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

    init {
        coroutineScope.launch {
            refresh()
        }
    }

    override fun configure(anonymousId: String?) {
        clear()
        this.anonymousId = anonymousId
    }

    override fun getVariation(experiment: Experiment): Variation {
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
            getAssignments(NEVER)?.variations?.get(experimentIdentifier) ?: Control
        }
    }

    override fun refresh(force: Boolean) {
        refresh(refreshStrategy = if (force) ALWAYS else IF_STALE)
    }

    override fun clear() {
        logger.d("ExPlat: clearing cached assignments and active variations")
        activeVariations.clear()
        anonymousId = null
        coroutineScope.launch { repository.clearCache() }
    }

    private fun refresh(refreshStrategy: RefreshStrategy) {
        if (experimentIdentifiers.isNotEmpty()) {
            getAssignments(refreshStrategy)
        }
    }

    private fun getAssignments(refreshStrategy: RefreshStrategy): Assignments? {
        val cachedAssignments: Assignments? = repository.getCached()
        if (refreshStrategy == ALWAYS ||
            cachedAssignments == null ||
            (refreshStrategy == IF_STALE && assignmentsValidator.isStale(cachedAssignments))
        ) {
            coroutineScope.launch { fetchAssignments() }
        }
        return cachedAssignments
    }

    private suspend fun fetchAssignments() =
        repository.fetch(platform, experimentIdentifiers, anonymousId).fold(
            onSuccess = {
                logger.d("ExPlat: fetching assignments successful with result: $it")
            },
            onFailure = {
                logger.d("ExPlat: fetching assignments failed with result: $it")
            },
        )

    private enum class RefreshStrategy { ALWAYS, IF_STALE, NEVER }
}
