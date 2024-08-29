package com.example.sampletracksapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.automattic.android.experimentation.Experiment
import com.automattic.android.experimentation.ExperimentLogger
import com.automattic.android.experimentation.VariationsRepository
import com.example.sampletracksapp.databinding.DialogExperimentationBinding
import kotlinx.coroutines.GlobalScope
import java.io.File
import java.util.UUID

class ExperimentationDialogFragment : DialogFragment() {

    private var exPlat: VariationsRepository? = null

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
            setup.setOnClickListener {
                exPlat = VariationsRepository.create(
                    platform = platform.text.toString(),
                    experiments = experiments.text?.toString()?.split(",")?.map {
                        Experiment(identifier = it)
                    }?.toSet().orEmpty(),
                    cacheDir = context!!.cacheDir,
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
                exPlat?.refresh(force = true)
            }

            // Implementation detail. This is not a part of the SDK, used here for testing purposes.
            getCache.setOnClickListener {
                File(context!!.cacheDir, "assignments.json").apply {
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
