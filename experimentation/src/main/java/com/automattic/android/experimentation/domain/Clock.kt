package com.automattic.android.experimentation.domain

internal fun interface Clock {
    fun currentTimeMillis(): Long
}

internal class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
