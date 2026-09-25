package com.softyorch.stroopoverload.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * Resolves a string resource for a ViewModel that produces user-facing messages.
 * Injected rather than read from an Application so those ViewModels run under
 * plain JUnit, where a test resolver can stand in for Android resources.
 */
fun interface StringResolver {
    fun get(@StringRes resId: Int, vararg args: Any): String
}

class AndroidStringResolver(context: Context) : StringResolver {
    private val appContext = context.applicationContext

    // Resources.getString(id, vararg) always runs String.format, even with no arguments, so a
    // plain message holding a literal '%' (a translator's "100%") would throw. Only format
    // when there is something to format.
    override fun get(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) appContext.getString(resId) else appContext.getString(resId, *args)
}
