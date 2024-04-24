package com.automattic.android.tracks.crashlogging.internal

import android.content.Context
import android.content.pm.ApplicationInfo

internal interface ApplicationInfoProvider {
    val debuggable: Boolean
}

internal class AndroidApplicationInfoProvider(context: Context) : ApplicationInfoProvider {
    override val debuggable: Boolean = context.applicationInfo.let {
        it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
