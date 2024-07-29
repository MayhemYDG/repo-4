package com.automattic.android.tracks.crashlogging.performance

import androidx.compose.ui.Modifier
import io.sentry.compose.SentryModifier.sentryTag

object PerformanceMetricsTag {
    @JvmStatic
    fun Modifier.tagForPerformanceMetrics(tag: String): Modifier {
        return sentryTag(tag)
    }
}
