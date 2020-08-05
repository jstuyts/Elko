package org.elkoserver.foundation.timer

internal class TimerThreadTimer internal constructor(private val myThread: TimerThread) : Timer {

    override fun after(millis: Long, target: TimeoutNoticer): Timeout {
        val newTimeout = TimerThreadTimeout(myThread, target)
        myThread.setTimeout(false, millis, newTimeout)
        return newTimeout
    }

    override fun every(resolution: Long, target: TickNoticer): Clock = TimerThreadClock(myThread, resolution, target)
}
