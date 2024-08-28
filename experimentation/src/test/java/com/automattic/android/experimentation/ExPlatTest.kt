package com.automattic.android.experimentation

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.AssignmentsValidator
import com.automattic.android.experimentation.domain.Clock
import com.automattic.android.experimentation.domain.Variation
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.domain.Variation.Treatment
import com.automattic.android.experimentation.local.FileBasedCache
import com.automattic.android.experimentation.remote.ExPlatUrlBuilder
import com.automattic.android.experimentation.remote.ExperimentRestClient
import com.automattic.android.experimentation.remote.MockWebServerUrlBuilder
import com.automattic.android.experimentation.repository.AssignmentsRepository
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

@ExperimentalCoroutinesApi
internal class ExPlatTest {
    private val server: MockWebServer = MockWebServer()
    private val platform = "wpandroid"
    private lateinit var tempCache: FileBasedCache

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
            val firstGet = exPlat.getVariation(testExperiment)
            exPlat.forceRefresh()
            runCurrent()

            val secondGet = exPlat.getVariation(testExperiment)

            // Even though the cache was updated...
            assertThat(tempCache.latest!!.variations[testExperimentName]).isEqualTo(Treatment("variation2"))
            // ...the second `get` should return the same value as the first one
            assertThat(secondGet).isEqualTo(firstGet).isEqualTo(Control)
        }

    @Test
    fun `in debug, getting variation throws an exception experiment was not found`() = runTest {
        val exPlat = createExPlat(experiments = emptySet())

        assertThatThrownBy {
            exPlat.getVariation(testExperiment)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("experiment not found")
    }

    private val testExperimentName = "testExperiment"
    private val testVariationName = "testVariation"
    private val testExperiment = object : Experiment {
        override val identifier: String = testExperimentName
    }
    private val testVariation = Treatment(testVariationName)
    private val testAssignment = Assignments(
        variations = mapOf(testExperimentName to testVariation),
        timeToLive = 3600,
        fetchedAt = 0L,
    )

    private fun TestScope.createExPlat(
        clock: Clock = Clock { 0 },
        experiments: Set<Experiment> = setOf(testExperiment),
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
                            "$testExperimentName": "$variationName"
                        },
                        "ttl": 3600
                    }
                    """.trimIndent(),
                ),
        )
    }
}
