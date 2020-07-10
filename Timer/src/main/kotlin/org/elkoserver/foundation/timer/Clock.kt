package org.elkoserver.foundation.timer

/**
 * Object which calls the [TickNoticer.noticeTick] method on a target
 * object every *n* milliseconds.  Clocks can only be created by calling
 * the [every()][Timer.every] method on a [Timer] instance.
 *
 * A `Clock` can be started and stopped.
 *
 * @param myTarget  The timer thread we are running with.
 * @param myResolution  How often we are to get tick events.
 *
 * @see TickNoticer
 */
class Clock internal constructor(private val myThread: TimerThread, private val myResolution: Long, private val myTarget: TickNoticer) : TimerWatcher() {

    /** Current run state  */
    private var amTicking = false

    /**
     * Gets the current tick number.  This is the number of times this clock
     * has ticked (i.e., invoked its [TickNoticer]) since it was created.
     *
     * @return the current tick count.
     */
    /** Current tick number  */
    private var ticks = 0

    /**
     * Called by the timer thread at clock tick time.
     */
    public override fun handleTimeout() {
        if (amTicking) {
            ++ticks
            myTarget.noticeTick(ticks)
        }
    }

    /**
     * Starts this clock from the current tick.
     */
    @Synchronized
    fun start() {
        if (!amTicking) {
            amTicking = true
            myThread.setTimeout(true, myResolution, this)
        }
    }

    /**
     * Stops this clock from ticking.  It can be restarted with [.start].
     */
    fun stop() {
        if (amTicking) {
            myThread.cancelTimeout(myEvent)
            myEvent = null
            amTicking = false
        }
    }
}
