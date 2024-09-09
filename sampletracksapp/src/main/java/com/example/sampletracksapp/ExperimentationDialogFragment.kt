package com.example.sampletracksapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.automattic.android.experimentation.Experiment
import com.automattic.android.experimentation.ExperimentLogger
import com.automattic.android.experimentation.VariationsRepository
import com.example.sampletracksapp.databinding.DialogExperimentationBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

class ExperimentationDialogFragment : DialogFragment() {

    private var exPlat: MutableStateFlow<VariationsRepository?> = MutableStateFlow(null)

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
            val coroutineScope = CoroutineScope(Dispatchers.Default)
            val cacheDir = requireContext().cacheDir

            val setupAvailabilityWatcher = SetupAvailabilityWatcher(this)
            platform.addTextChangedListener(setupAvailabilityWatcher)
            experiments.addTextChangedListener(setupAvailabilityWatcher)

            output.movementMethod = ScrollingMovementMethod.getInstance()

            coroutineScope.launch {
                exPlat.collect { exPlat ->
                    withContext(Dispatchers.Main) {
                        arrayOf(getVariations, generateAnonId, clear).forEach {
                            it.isEnabled = exPlat != null
                        }
                    }
                }
            }

            initialize.setOnClickListener {
                exPlat.value = VariationsRepository.create(
                    platform = platform.text.toString(),
                    experiments = experiments.text?.toString()?.split(",")?.map {
                        Experiment(identifier = it)
                    }?.toSet().orEmpty(),
                    cacheDir = cacheDir,
                    coroutineScope = coroutineScope,
                    failFast = true,
                    logger = object : ExperimentLogger {
                        override fun d(message: String) {
                            log(coroutineScope, message)
                        }

                        override fun e(message: String, throwable: Throwable?) {
                            log(coroutineScope, message)
                        }
                    },
                ).apply {
                    initialize(anonId.text?.toString().orEmpty())
                }
            }

            getVariations.setOnClickListener {
                experiments.text?.toString()?.split(",")?.forEach { experiment ->
                    exPlat.value?.getVariation(Experiment(identifier = experiment)).let { variation ->
                        log(coroutineScope, "$experiment: $variation")
                    }
                }
            }

            // Implementation detail. This is not a part of the SDK, used here for testing purposes.
            getCache.setOnClickListener {
                File(cacheDir, "assignments.json").apply {
                    if (exists()) {
                        readText().let {
                            if (it.isEmpty()) {
                                log(coroutineScope, "Cache is empty")
                            } else {
                                log(coroutineScope, it)
                            }
                        }
                    } else {
                        log(coroutineScope, "Cache does not exist")
                    }
                }
            }

            generateAnonId.setOnClickListener {
                anonId.setText(UUID.randomUUID().toString())
            }

            clear.setOnClickListener {
                exPlat.value?.clear()
            }

            clearLog.setOnClickListener {
                output.text = ""
            }

            return root
        }
    }

    @SuppressLint("SetTextI18n")
    private fun DialogExperimentationBinding.log(
        coroutineScope: CoroutineScope,
        message: String,
        throwable: Throwable? = null,
    ) {
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                val date = Date()
                val format = DateFormat.getTimeFormat(requireContext())
                output.text = "${format.format(date)} \t $message\n${output.text}"
            }
        }

        if (throwable != null) {
            Log.e("ExPlat", message, throwable)
        } else {
            Log.d("ExPlat", message)
        }
    }

    private class SetupAvailabilityWatcher(
        private val binding: DialogExperimentationBinding,
    ) : TextWatcher {

        init {
            manageSetupAvailability()
        }

        override fun afterTextChanged(s: Editable?) {
            manageSetupAvailability()
        }

        private fun manageSetupAvailability() {
            binding.initialize.isEnabled = binding.platform.text?.isNotEmpty() == true &&
                binding.experiments.text?.isNotEmpty() == true
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    }
}
