package com.automattic.android.experimentation.domain

sealed class Variation{
    data class Treatment(val name: String) : Variation()
    data object Control : Variation()
}
