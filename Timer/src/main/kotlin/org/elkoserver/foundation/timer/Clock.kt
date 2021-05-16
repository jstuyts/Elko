package org.elkoserver.foundation.timer

/**
 * Object which calls the [TickNoticer.noticeTick] method on a target
 * object every *n* milliseconds.  Clocks can only be created by calling
 * the [every()][Timer.every] method on a [Timer] instance.
 *
 * A `Clock` can be started and stopped.
 *
 * @see TickNoticer
 */
interface Clock {

    /**
     * Starts this clock from the current tick.
     */
    fun start()

    /**
     * Stops this clock from ticking.  It can be restarted with [start].
     */
    fun stop()
}
