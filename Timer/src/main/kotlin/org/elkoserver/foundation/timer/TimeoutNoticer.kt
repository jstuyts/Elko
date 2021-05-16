package org.elkoserver.foundation.timer

/**
 * Interface implemented by objects that wish to be informed about [ ] events.  A [Timeout] is created by calling the [ ][Timer.after] method and passing it an object that implements
 * this `TimeoutNoticer` interface.  The [noticeTimeout] method
 * of the `TimeoutNoticer` object will be invoked after the indicated
 * interval.
 *
 * @see Timer.after Timer.after
 * @see Timeout
 */
interface TimeoutNoticer {
    /**
     * Notification of a timeout event.
     */
    fun noticeTimeout()
}