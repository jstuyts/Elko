package org.elkoserver.foundation.timer.timerthread

import org.elkoserver.foundation.timer.Clock
import org.elkoserver.foundation.timer.TickNoticer
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer

internal class TimerThreadTimer internal constructor(private val myThread: TimerThread) : Timer {

    override fun after(millis: Long, target: TimeoutNoticer): Timeout {
        val newTimeout = TimerThreadTimeout(myThread, target)
        myThread.setTimeout(false, millis, newTimeout)
        return newTimeout
    }

    override fun every(resolution: Long, target: TickNoticer): Clock = TimerThreadClock(myThread, resolution, target)
}
