package org.elkoserver.foundation.boot

internal class ThrowableHolder {
    @Volatile
    internal var throwable: Throwable? = null
}