package org.elkoserver.foundation.timer

/**
 * Object representing a scheduled timeout event.  Timeouts can only be created
 * by calling the [after()][Timer.after] method on a [Timer]
 * instance.
 *
 * @see TimeoutNoticer
 */
interface Timeout {

    /**
     * Cancels this timeout.  Note, however, that although a `Timeout`
     * can be cancelled, there is no guarantee that it has not already occured
     * by the time it is cancelled.
     *
     * @return `true` if cancellation was successful, `false` if
     * it wasn't.
     */
    fun cancel(): Boolean
}
