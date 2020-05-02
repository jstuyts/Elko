package org.elkoserver.foundation.timer

abstract class TimerWatcher {
    @JvmField
    internal var myEvent: TimerQEntry? = null

    /**
     * Notification (from within the package) that the timeout has tripped.
     */
    internal abstract fun handleTimeout()

    internal fun setEvent(event: TimerQEntry?) {
        myEvent = event
    }
}
