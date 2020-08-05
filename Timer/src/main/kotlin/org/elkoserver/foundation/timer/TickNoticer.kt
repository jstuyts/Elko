package org.elkoserver.foundation.timer

/**
 * Interface implemented by classes that want to be informed about repeated
 * ticks from a [Clock].  A [Clock] is created by calling the
 * [Timer.every()][Timer.every] method and passing it an object which
 * implements this `TickNoticer` interface.  The [.noticeTick]
 * method of the `TickNoticer` object will be invoked periodically at
 * the indicated frequency.
 *
 * @see imer.every Timer.every
 * @see Clock
 */
interface TickNoticer {
    /**
     * Called by clocks on their targets after each tick.
     *
     * @param ticks  Number of ticks since the calling clock was started.
     */
    fun noticeTick(ticks: Int)
}