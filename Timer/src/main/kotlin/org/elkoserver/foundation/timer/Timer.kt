package org.elkoserver.foundation.timer

/**
 * The master control object for scheduling timed events using timeouts and
 * clocks.  One-time events (controlled by [Timeout] objects) may be
 * scheduled by calling either of the [after] methods.
 * Recurring events (controlled by [Clock] objects) may be scheduled by
 * calling either of the [every] methods.
 *
 * Event notification is guaranteed to be prompt but not immediate: the event
 * handler will be invoked no sooner than scheduled and as soon thereafter as
 * possible, but no guarantees are offered that somewhat more time will not
 * have passed than was requested.  In particular, while the scheduling API
 * lets you specify times with millisecond precision, millisecond accuracy in
 * practice should not be assumed.
 */
interface Timer {

    /**
     * Sets a timeout for the specified number of milliseconds.  After the
     * timer expires, `target`'s [ noticeTimeout()][TimeoutNoticer.noticeTimeout] method is called.  Notification is always asynchronous.
     *
     * @param millis  How long to wait until timing out.
     * @param target  Object to be informed when the time comes.
     *
     * @return a timeout object that can be used to cancel or identify the
     * timeout.
     *
     * @see TimeoutNoticer
     */
    fun after(millis: Long, target: TimeoutNoticer): Timeout

    /**
     * Creates a new clock.  The new clock begins life stopped with its tick
     * count at zero (start the clock ticking by calling its [ start()][Clock.start] method).  Clock ticks are always asynchronous.
     *
     * @param resolution  The clock tick interval.
     * @param target  Object to be sent tick notifications.
     *
     * @return a new clock object according to the given parameters.
     *
     * @see TickNoticer
     */
    fun every(resolution: Long, target: TickNoticer): Clock
}
