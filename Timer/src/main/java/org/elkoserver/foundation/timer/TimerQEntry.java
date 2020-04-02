package org.elkoserver.foundation.timer;

import java.time.Clock;

/**
 * An entry in the timer event queue.
 */
class TimerQEntry implements Comparable<TimerQEntry>
{
    boolean myRepeat;
    long myDelta;
    long myWhen;
    TimerWatcher myTarget;
    TimerQEntry myNext;

    TimerQEntry(boolean repeat, long delta, TimerWatcher target, Clock clock) {
        myRepeat = repeat;
        myDelta = delta;
        myWhen = clock.millis() + delta;
        myTarget = target;
        myNext = null;
    }

    public int compareTo(TimerQEntry other) {
        if (other != null) {
            return Long.compare(myWhen, other.myWhen);
        } else {
            throw new ClassCastException();
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof TimerQEntry) {
            TimerQEntry other = (TimerQEntry) obj;
            return myWhen == other.myWhen;
        } else {
            return false;
        }
    }
}
