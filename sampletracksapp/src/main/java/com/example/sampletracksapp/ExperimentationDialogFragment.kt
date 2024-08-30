package com.example.sampletracksapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.automattic.android.experimentation.ExPlat
import com.automattic.android.experimentation.Experiment
import com.automattic.android.experimentation.ExperimentLogger
import com.example.sampletracksapp.databinding.DialogExperimentationBinding
import kotlinx.coroutines.GlobalScope
import java.io.File
import java.util.UUID

class ExperimentationDialogFragment : DialogFragment() {

    private var exPlat: ExPlat? = null

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            it.window?.setLayout(width, height)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        DialogExperimentationBinding.inflate(inflater, container, false).apply {
            val cacheDir = requireContext().cacheDir
            setup.setOnClickListener {
                exPlat = ExPlat.create(
                    platform = platform.text.toString(),
                    experiments = experiments.text?.toString()?.split(",")?.map {
                        object : Experiment {
                            override val identifier: String = it
                        }
                    }?.toSet().orEmpty(),
                    cacheDir = cacheDir,
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
            return root
        }
    }
}
