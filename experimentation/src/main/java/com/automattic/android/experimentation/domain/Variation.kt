package com.automattic.android.experimentation.domain

sealed class Variation{
    class Treatment(val name: String) : Variation()
    object Control : Variation()
}
