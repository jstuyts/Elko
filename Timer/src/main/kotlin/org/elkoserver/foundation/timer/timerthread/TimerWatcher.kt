package org.elkoserver.foundation.timer.timerthread

internal abstract class TimerWatcher {
    internal var myEvent: TimerQueueEntry? = null

    /**
     * Notification (from within the package) that the timeout has tripped.
     */
    internal abstract fun handleTimeout()

    internal fun setEvent(event: TimerQueueEntry?) {
        myEvent = event
    }
}
