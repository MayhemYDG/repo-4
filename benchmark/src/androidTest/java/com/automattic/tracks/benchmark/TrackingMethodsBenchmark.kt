package com.automattic.tracks.benchmark

import android.app.Activity
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.automattic.android.tracks.TracksClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field

@RunWith(AndroidJUnit4::class)
class TrackingMethodsBenchmark {
    @get:Rule
    val activityRule = ActivityScenarioRule(SampleActivity::class.java)

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var tracksClient: TracksClient

    @Before
    fun setUp() {
        activityRule.scenario.onActivity {
            tracksClient = TracksClient.getClient(it)
        }

        val field: Field = TracksClient::class.java.getDeclaredField("mTracksRestEndpointURL")
        field.isAccessible = true
        field.set(tracksClient, "https://not-a-real-url.test")
    }

    @Test
    fun basicTrackMethod() {
        benchmarkRule.measureRepeated {
            tracksClient.track("test1_test2_final", "user", TracksClient.NosaraUserType.ANON)
        }
    }

    class SampleActivity : Activity()
}
