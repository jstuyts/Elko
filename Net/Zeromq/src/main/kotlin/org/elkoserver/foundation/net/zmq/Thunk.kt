package org.elkoserver.foundation.net.zmq

/**
 * An arbitrary (zero-argument) executable.  Similar to [Runnable] but
 * can throw an exception.
 *
 * @see java.lang.Runnable
 */
interface Thunk {
    /**
     * Execute this thunk.
     */
    fun run()
}
