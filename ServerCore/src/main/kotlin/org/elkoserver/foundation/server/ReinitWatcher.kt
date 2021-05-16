package org.elkoserver.foundation.server

/**
 * Interface implemented by objects that register to receive notification when
 * the server is reinitialized.
 */
fun interface ReinitWatcher {
    /**
     * Take note that the server has been reinitialized.
     *
     * @see Server.registerReinitWatcher
     */
    fun noteReinit()
}