package com.example.sampletracksapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.automattic.android.experimentation.ExPlat
import com.automattic.android.experimentation.Experiment
import com.automattic.android.experimentation.ExperimentLogger
import com.automattic.android.tracks.crashlogging.CrashLoggingDataProvider
import com.automattic.android.tracks.crashlogging.CrashLoggingOkHttpInterceptorProvider
import com.automattic.android.tracks.crashlogging.CrashLoggingProvider
import com.automattic.android.tracks.crashlogging.CrashLoggingUser
import com.automattic.android.tracks.crashlogging.EventLevel
import com.automattic.android.tracks.crashlogging.ExtraKnownKey
import com.automattic.android.tracks.crashlogging.JsException
import com.automattic.android.tracks.crashlogging.JsExceptionCallback
import com.automattic.android.tracks.crashlogging.JsExceptionStackTraceElement
import com.automattic.android.tracks.crashlogging.PerformanceMonitoringConfig
import com.automattic.android.tracks.crashlogging.ReleaseName
import com.automattic.android.tracks.crashlogging.RequestFormatter
import com.automattic.android.tracks.crashlogging.performance.PerformanceMonitoringRepositoryProvider
import com.automattic.android.tracks.crashlogging.performance.PerformanceTransactionRepository
import com.automattic.android.tracks.crashlogging.performance.TransactionOperation
import com.automattic.android.tracks.crashlogging.performance.TransactionStatus
import com.example.sampletracksapp.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOf
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeoutException

class MainActivity : AppCompatActivity() {
    val transactionRepository: PerformanceTransactionRepository =
        PerformanceMonitoringRepositoryProvider.createInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashLogging = CrashLoggingProvider.createInstance(
            application,
            object : CrashLoggingDataProvider {
                override val sentryDSN = BuildConfig.SENTRY_TEST_PROJECT_DSN
                override val buildType = BuildConfig.BUILD_TYPE
                override val releaseName = ReleaseName.SetByApplication("test")
                override val locale = Locale.US
                override val enableCrashLoggingLogs = true
                override val performanceMonitoringConfig =
                    PerformanceMonitoringConfig.Enabled(sampleRate = 1.0, profilesSampleRate = 1.0)
                override val user = flowOf(
                    CrashLoggingUser(
                        userID = "test user id",
                        email = "test@user.com",
                        username = "test username",
                    ),
                )
                override val applicationContextProvider =
                    flowOf(mapOf("extra" to "application context"))

                override fun shouldDropWrappingException(
                    module: String,
                    type: String,
                    value: String,
                ): Boolean {
                    return false
                }

                override fun crashLoggingEnabled(): Boolean {
                    return true
                }

                override fun extraKnownKeys(): List<String> {
                    return emptyList()
                }

                override fun provideExtrasForEvent(
                    currentExtras: Map<ExtraKnownKey, String>,
                    eventLevel: EventLevel,
                ): Map<ExtraKnownKey, String> {
                    return mapOf("extra" to "event value")
                }
            },
            appScope = GlobalScope,
        )

        crashLogging.initialize()

        ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)

            sendReportWithMessage.setOnClickListener {
                crashLogging.sendReport(message = "Message from Tracks test app")
            }

            sendReportWithException.setOnClickListener {
                crashLogging.sendReport(exception = Exception("Exception from Tracks test app"))
            }

            sendReportWithJavaScriptException.setOnClickListener {
                val callback = object : JsExceptionCallback {
                    override fun onReportSent(sent: Boolean) {
                        Log.d("JsExceptionCallback", "onReportSent: $sent")
                    }
                }
                val jsException = JsException(
                    type = "Error",
                    message = "JavaScript exception from Tracks test app",
                    stackTrace = listOf(
                        JsExceptionStackTraceElement(
                            fileName = "file.js",
                            lineNumber = 1,
                            colNumber = 1,
                            function = "function",
                        ),
                    ),
                    context = mapOf("context" to "value"),
                    tags = mapOf("tag" to "SomeTag"),
                    isHandled = true,
                    handledBy = "SomeHandler",
                )
                crashLogging.sendJavaScriptReport(jsException, callback)
            }

            recordBreadcrumbWithMessage.setOnClickListener {
                crashLogging.recordEvent(
                    message = "Custom breadcrumb",
                    category = "Custom category",
                )
            }

            recordBreadcrumbWithException.setOnClickListener {
                crashLogging.recordException(
                    exception = NullPointerException(),
                    category = "Custom exception category",
                )
            }

            val okHttp = OkHttpClient.Builder().addInterceptor(
                CrashLoggingOkHttpInterceptorProvider.createInstance(object : RequestFormatter {
                    override fun formatRequestUrl(request: Request): String {
                        return "Url formatted by RequestFormatter"
                    }
                }),
            ).build()

            executePerformanceTransaction.setOnClickListener {
                val transactionId = transactionRepository.startTransaction(
                    "test name",
                    TransactionOperation.UI_LOAD,
                )

                okHttp.newCall(
                    Request.Builder()
                        .url("https://jsonplaceholder.typicode.com/posts/1")
                        .build(),
                ).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        transactionRepository.finishTransaction(
                            transactionId,
                            TransactionStatus.ABORTED,
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val spanId = transactionRepository.startSpan(
                            transactionId,
                            "test span",
                            "test operation",
                        )

                        Thread.sleep(1000)

                        transactionRepository.finishSpan(spanId, 408, TimeoutException())

                        transactionRepository.finishTransaction(
                            transactionId,
                            TransactionStatus.SUCCESSFUL,
                        )
                    }
                })
            }

            setupExperimentationTesting()
        }
    }

    private fun ActivityMainBinding.setupExperimentationTesting() {
        var exPlat: ExPlat? = null

        setup.setOnClickListener {
            exPlat = ExPlat.create(
                platform = platform.text.toString(),
                experiments = experiments.text?.toString()?.split(",")?.map {
                    object : Experiment {
                        override val identifier: String = it
                    }
                }?.toSet().orEmpty(),
                cacheDir = this@MainActivity.cacheDir,
                coroutineScope = GlobalScope,
                isDebug = true,
                logger = object : ExperimentLogger {
                    override fun d(message: String) {
                        Log.d("ExPlat", message)
                    }

                    override fun e(message: String, throwable: Throwable) {
                        Log.e("ExPlat", message, throwable)
                    }
                },
            ).apply {
                configure(anonId.text?.toString().orEmpty())
            }
        }

        fetch.setOnClickListener {
            exPlat?.forceRefresh()
        }

        // Implementation detail. This is not a part of the SDK, used here for testing purposes.
        getCache.setOnClickListener {
            File(cacheDir, "assignments.json").apply {
                if (exists()) {
                    readText().let {
                        if (it.isEmpty()) {
                            Log.d("ExPlat", "Cache is empty")
                        } else {
                            Log.d("ExPlat", it)
                        }
                    }
                }
            }
        }

        generateAnonId.setOnClickListener {
            anonId.setText(UUID.randomUUID().toString())
            exPlat?.clear()
        }

        clearCache.setOnClickListener {
            exPlat?.clear()
        }
    }
}
