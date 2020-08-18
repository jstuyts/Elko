package org.elkoserver.foundation.timer.timerthread

import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer

internal class TimerThreadTimeout internal constructor(private var myThread: TimerThread?, private val myTarget: TimeoutNoticer) : TimerWatcher(), Timeout {

    override fun cancel(): Boolean {
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
