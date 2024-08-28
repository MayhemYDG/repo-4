package com.automattic.android.experimentation

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.Clock
import com.automattic.android.experimentation.domain.SystemClock
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.domain.Variation.Treatment
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExPlatUrlBuilder
import com.automattic.android.experimentation.remote.ExperimentRestClient
import com.automattic.android.experimentation.remote.MockWebServerUrlBuilder
import com.automattic.android.experimentation.repository.AssignmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.io.path.createTempDirectory
import org.assertj.core.api.Assertions.assertThatThrownBy

@ExperimentalCoroutinesApi
internal class ExPlatTest {
    private val server: MockWebServer = MockWebServer()
    private val platform = "wpandroid"
    private val cache: FileBasedCache = mock()
    private val restClient: ExperimentRestClient = mock()
    private val logger: ExperimentLogger = mock()
    private var exPlat: ExPlat = createExPlat(
        isDebug = false,
        experiments = emptySet(),
    )
    private val testExperimentName = "dummy"
    private val dummyExperiment = object : Experiment {
        override val identifier: String = testExperimentName
    }
    lateinit var tempCache: FileBasedCache

    @Test
    fun `refreshing in case of empty cache is successful`() = runTest {
        enqueueSuccessfulNetworkResponse()
        val exPlat = createExPlat()

        exPlat.refreshIfNeeded()
        runCurrent()

        assertThat(tempCache.latest).isEqualTo(testAssignment)
    }

    @Test
    fun `refreshing in case of stale cache is successful`() = runTest {
        enqueueSuccessfulNetworkResponse()
        val exPlat = createExPlat(clock = { 3601 })
        tempCache.saveAssignments(testAssignment.copy(timeToLive = 3600, fetchedAt = 0))

        exPlat.refreshIfNeeded()
        runCurrent()

        assertThat(tempCache.latest).isEqualTo(
            testAssignment.copy(fetchedAt = 3601),
        )
    }

    @Test
    fun `refreshing in case of fresh cache doesn't fetch new assignments`() = runTest {
        enqueueSuccessfulNetworkResponse()
        val exPlat = createExPlat(clock = { 3599 })
        tempCache.saveAssignments(testAssignment.copy(timeToLive = 3600))

        exPlat.refreshIfNeeded()
        runCurrent()

        assertThat(tempCache.latest).isEqualTo(
            testAssignment,
        )
    }

    @Test
    fun `force refreshing fetches new assignments, even if cache is fresh`() = runTest {
        enqueueSuccessfulNetworkResponse()
        val exPlat = createExPlat(clock = { 3599 })
        tempCache.saveAssignments(testAssignment.copy(timeToLive = 3600))

        exPlat.forceRefresh()
        runCurrent()

        assertThat(tempCache.latest).isEqualTo(
            testAssignment.copy(fetchedAt = 3599),
        )
    }

    @Test
    fun `clearing removes cached data`() = runTest {
        val exPlat = createExPlat()
        tempCache.saveAssignments(testAssignment)

        exPlat.clear()
        runCurrent()

        assertThat(tempCache.latest).isNull()
    }

    /*
    This scenario is about returning the same variation during a single session.
    We don't want SDK consumers to mix variations during the same session,
    as it could lead to unexpected behavior.
     */
    @Test
    fun `getting variation for the second time returns the same value, even if cache was updated`() =
        runTest {
            enqueueSuccessfulNetworkResponse(variation = Treatment("variation2"))
            val exPlat = createExPlat(clock = { 123 })
            tempCache.saveAssignments(
                testAssignment.copy(
                    mapOf(testExperimentName to Control), fetchedAt = 0
                )
            )
            val firstGet = exPlat.getVariation(dummyExperiment)
            exPlat.forceRefresh()
            runCurrent()

            val secondGet = exPlat.getVariation(dummyExperiment)

            // Even though the cache was updated...
            assertThat(tempCache.latest!!.variations[testExperimentName]).isEqualTo(Treatment("variation2"))
            // ...the second `get` should return the same value as the first one
            assertThat(secondGet).isEqualTo(firstGet).isEqualTo(Control)
        }

    @Test
    fun `in debug, getting variation throws an exception experiment was not found`() = runTest {
        val exPlat = createExPlat(experiments = emptySet())

        assertThatThrownBy {
            exPlat.getVariation(dummyExperiment)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("experiment not found")
    }

    private fun createExPlat(isDebug: Boolean, experiments: Set<Experiment>): ExPlat =
        ExPlat(
            platform = platform,
            experiments = experiments,
            logger = logger,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            isDebug = isDebug,
            assignmentsValidator = AssignmentsValidator(SystemClock()),
            repository = AssignmentsRepository(restClient, cache),
        )

    private fun TestScope.createExPlat(
        clock: Clock = Clock { 0 },
        experiments: Set<Experiment> = setOf(dummyExperiment),
    ): ExPlat {
        val coroutineScope = this
        val dispatcher = StandardTestDispatcher(coroutineScope.testScheduler)
        val restClient = ExperimentRestClient(
            urlBuilder = MockWebServerUrlBuilder(ExPlatUrlBuilder(), server),
            dispatcher = dispatcher,
            clock = clock,
        )
        tempCache = FileBasedCache(
            createTempDirectory().toFile(),
            dispatcher = dispatcher,
            scope = coroutineScope
        )

        return ExPlat(
            platform = platform,
            experiments = experiments,
            logger = object : ExperimentLogger {
                override fun d(message: String) = Unit
                override fun e(message: String, throwable: Throwable) = Unit
            },
            coroutineScope = coroutineScope.backgroundScope,
            isDebug = true,
            assignmentsValidator = AssignmentsValidator(clock = clock),
            repository = AssignmentsRepository(restClient, tempCache),
        )
    }

    private val testAssignment = Assignments(
        variations = mapOf(testExperimentName to Treatment("variation1")),
        timeToLive = 3600,
        fetchedAt = 0L,
    )

    private val testVariation = Treatment("variation1")

    private fun enqueueSuccessfulNetworkResponse(variation: Variation = testVariation) {
        val variationName = when (variation) {
            is Control -> "control"
            is Treatment -> variation.name
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "variations": {
                            "dummy": "$variationName"
                        },
                        "ttl": 3600
                    }
                    """.trimIndent(),
                ),
        )
    }

    private suspend fun setupAssignments(
        cachedAssignments: Assignments?,
        fetchedAssignments: Assignments
    ) {
        whenever(cache.getAssignments()).thenReturn(cachedAssignments)
        whenever(restClient.fetchAssignments(eq(platform), any(), anyOrNull()))
            .thenReturn(Result.success(fetchedAssignments))
    }

    private fun buildAssignments(
        isStale: Boolean = false,
        variations: Map<String, Variation> = emptyMap(),
    ): Assignments {
        val now = 123456789L
        val oneHourAgo = now - ONE_HOUR_IN_SECONDS
        val oneHourFromNow = now + ONE_HOUR_IN_SECONDS
        return if (isStale) {
            Assignments(variations, ONE_HOUR_IN_SECONDS, oneHourAgo)
        } else {
            Assignments(variations, ONE_HOUR_IN_SECONDS, oneHourFromNow)
        }
    }

    companion object {
        private const val ONE_HOUR_IN_SECONDS = 3600
    }
}
