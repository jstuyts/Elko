package org.elkoserver.foundation.timer

/**
 * Object representing a scheduled timeout event.  Timeouts can only be created
 * by calling the [after()][Timer.after] method on a [Timer]
 * instance.
 *
 * @param myThread  The timer thread responsible for this timeout.
 * @param myTarget  The object to be notified at timeout time
 *
 * @see TimeoutNoticer
 */
class Timeout internal constructor(private var myThread: TimerThread?, private val myTarget: TimeoutNoticer) : TimerWatcher() {

    /**
     * Cancels this timeout.  Note, however, that although a `Timeout`
     * can be cancelled, there is no guarantee that it has not already occured
     * by the time it is cancelled.
     *
     * @return `true` if cancellation was successful, `false` if
     * it wasn't.
     */
    fun cancel(): Boolean {
        val currentThread = myThread
        return if (currentThread == null) {
            false
        } else {
            val result = currentThread.cancelTimeout(myEvent)
            myThread = null
            result
        }
    }

    /**
     * Called by the timer thread when the timeout time comes.
     */
    override fun handleTimeout() {
        myTarget.noticeTimeout()
    }
}
