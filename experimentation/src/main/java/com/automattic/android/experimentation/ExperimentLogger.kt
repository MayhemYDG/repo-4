package com.automattic.android.experimentation

interface ExperimentLogger {
    fun d(message: String)
    fun e(message: String, throwable: Throwable)
}
