package com.automattic.android.experimentation.local

import com.automattic.android.experimentation.domain.Assignments
import com.automattic.android.experimentation.domain.Variation.Control
import com.automattic.android.experimentation.domain.Variation.Treatment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
internal class FileBasedCacheTest {

    private lateinit var nonExistingCacheDir: File

    @Before
    fun setUp() {
        nonExistingCacheDir = File("build/non-existent")
    }

    @Test
    fun `saving and reading assignments is successful`() = runTest {
        val sut = fileBasedCache(this)
        sut.saveAssignments(TEST_ASSIGNMENTS)

        val result = sut.getAssignments()

        assertEquals(TEST_ASSIGNMENTS, result)
    }

    @Test
    fun `updating assignments is successful`() = runTest {
        val sut = fileBasedCache(this)
        val updatedTestAssignments = TEST_ASSIGNMENTS.copy(
            variations = mapOf(
                "experiment1" to Treatment("variation1"),
                "experiment2" to Control,
            ),
            fetchedAt = 987654321L,
        )
        sut.saveAssignments(TEST_ASSIGNMENTS)

        sut.saveAssignments(updatedTestAssignments)
        val result = sut.getAssignments()

        assertEquals(updatedTestAssignments, result)
    }

    @Test
    fun `getting assignments from empty cache returns no results`() = runTest {
        val sut = fileBasedCache(this)

        val result = sut.getAssignments()

        assertNull(result)
    }

    @Test
    fun `clearing empty cache has no effect`() = runTest {
        val sut = fileBasedCache(this)

        sut.clear()
    }

    @Test
    fun `saving cache when cache dir doesnt exist is successful`() = runTest {
        nonExistingCacheDir.apply { assert(!this.exists()) }
        val sut = fileBasedCache(this, cacheDir = nonExistingCacheDir)
        sut.saveAssignments(TEST_ASSIGNMENTS)

        val result = sut.getAssignments()

        assertEquals(TEST_ASSIGNMENTS, result)
    }

    @After
    fun tearDown() {
        nonExistingCacheDir.deleteRecursively()
    }

    private fun fileBasedCache(
        scope: TestScope,
        cacheDir: File = createTempDirectory().toFile(),
    ) = FileBasedCache(
        cacheDir = cacheDir,
        dispatcher = StandardTestDispatcher(scope.testScheduler),
        scope = scope,
    )

    companion object {
        private val TEST_ASSIGNMENTS = Assignments(
            variations = mapOf(
                "experiment1" to Control,
                "experiment2" to Treatment("variation2"),
            ),
            timeToLive = 3600,
            fetchedAt = 123456789L,
            anonymousId = "id",
        )
    }
}
