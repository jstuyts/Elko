package org.elkoserver.foundation.timer

import java.time.Clock

/**
 * An entry in the timer event queue.
 */
internal class TimerQueueEntry(internal var myRepeat: Boolean, internal var myDelta: Long, var myTarget: TimerWatcher, clock: Clock) : Comparable<TimerQueueEntry?> {
    var myWhen = clock.millis() + myDelta

    var myNext: TimerQueueEntry? = null

    override fun compareTo(other: TimerQueueEntry?) =
            if (other != null) {
                myWhen.compareTo(other.myWhen)
            } else {
                throw ClassCastException()
            }

    override fun equals(other: Any?): Boolean {
        return if (other is TimerQueueEntry) {
            myWhen == other.myWhen
        } else {
            false
        }
    }

    override fun hashCode(): Int = myWhen.hashCode()
}
