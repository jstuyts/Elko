package org.elkoserver.foundation.timer

import java.time.Clock

/**
 * An entry in the timer event queue.
 */
internal class TimerQEntry(internal var myRepeat: Boolean, internal var myDelta: Long, var myTarget: TimerWatcher, clock: Clock) : Comparable<TimerQEntry?> {
    @JvmField
    var myWhen = clock.millis() + myDelta

    @JvmField
    var myNext: TimerQEntry? = null

    override fun compareTo(other: TimerQEntry?) =
            if (other != null) {
                myWhen.compareTo(other.myWhen)
            } else {
                throw ClassCastException()
            }

    override fun equals(other: Any?): Boolean {
        return if (other is TimerQEntry) {
            myWhen == other.myWhen
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return myWhen.hashCode()
    }
}
