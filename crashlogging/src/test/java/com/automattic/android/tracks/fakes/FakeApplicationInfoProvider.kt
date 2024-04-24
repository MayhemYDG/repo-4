package com.automattic.android.tracks.fakes

import com.automattic.android.tracks.crashlogging.internal.ApplicationInfoProvider

class FakeApplicationInfoProvider(var nextDebuggable: Boolean = false) : ApplicationInfoProvider {
    override val debuggable
        get() = nextDebuggable
}
