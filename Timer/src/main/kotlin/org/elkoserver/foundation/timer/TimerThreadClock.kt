package org.elkoserver.foundation.timer

internal class TimerThreadClock internal constructor(private val myThread: TimerThread, private val myResolution: Long, private val myTarget: TickNoticer) : TimerWatcher(), Clock {

    /** Current run state  */
    private var amTicking = false

    /**
     * Current tick number.  This is the number of times this clock
     * has ticked (i.e., invoked its [TickNoticer]) since it was created.
     */
    private var ticks = 0

    /**
     * Called by the timer thread at clock tick time.
     */
    override fun handleTimeout() {
        if (amTicking) {
            ++ticks
            myTarget.noticeTick(ticks)
        }
    }

    @Synchronized
    override fun start() {
        if (!amTicking) {
            amTicking = true
            myThread.setTimeout(true, myResolution, this)
        }
    }

    override fun stop() {
        if (amTicking) {
            myThread.cancelTimeout(myEvent)
            myEvent = null
            amTicking = false
        }
    }
}
