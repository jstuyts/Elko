package org.elkoserver.foundation.server

/**
 * Interface implemented by objects that register to be notified when the
 * server is about to be shut down.
 */
fun interface ShutdownWatcher {
    /**
     * Take note that the server is about to be shut down.
     */
    fun noteShutdown()
}