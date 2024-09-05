package com.automattic.android.experimentation.domain

public sealed class Variation {
    public data class Treatment(val name: String) : Variation()
    public data object Control : Variation()
}
