package com.automattic.android.experimentation

public interface ExperimentLogger {
    public fun d(message: String)
    public fun e(message: String, throwable: Throwable)
}
