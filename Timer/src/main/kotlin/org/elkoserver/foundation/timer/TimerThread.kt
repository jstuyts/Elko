package org.elkoserver.foundation.timer

import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import java.time.Clock
import java.util.TreeMap

/**
 * Thread to handle timeouts and clocks.
 */
internal class TimerThread(private val clock: Clock, private val exceptionReporter: ExceptionReporter) : Thread("Elko Timer") {

    /**
     * Collection of pending timer events, sorted by time
     */
    private var myEvents: TreeMap<TimerQEntry, TimerQEntry> = TreeMap()

    /**
     * Flag to control execution
     */
    private var myRunning = true
    fun cancelTimeout(event: TimerQEntry): Boolean {
        synchronized(this) { return myEvents.remove(event) != null }
    }

    /**
     * Insert a new event into the timer queue (in order).
     *
     * @param newEntry A TimerQEntry describing the new event.
     */
    private fun insertEntry(newEntry: TimerQEntry) {
        synchronized(this) {
            while (myEvents[newEntry] != null) {
                /* All times must be unique.  */
                // XXX TODO: This is a very ugly hack.  It will need to be
                // revisited should we find ourselves in a use case that
                // entails scheduling numerous events within a small time
                // window, both because the linear search for an unused time
                // position will get expensive, and because of the actual
                // delays introduced by artificially incrementing the trigger
                // time by a large amount.  Current experience is that two
                // events being scheduled at the same time is very rare, and
                // three is nearly unheard of, but this observation is based on
                // a sparsely explored application space.  The obvious fix
                // would be to replace each entry for a given time slot with a
                // list of entries, but the extra allocation overhead is not
                // yet justified by need.
                newEntry.myWhen++
            }
            myEvents.put(newEntry, newEntry)
        }
    }

    /**
     * Return the current clock time, in milliseconds
     */
    fun queryTimerMillis() = clock.millis()

    /**
     * Run the timer thread until told to stop.
     */
    override fun run() {
        while (myRunning) {
            runloop()
        }
    }

    /**
     * The actual guts of the timer thread: Look for the next event on the
     * timer queue.  Wait until the indicated time.  Process the event and any
     * others that may now be relevant.  Repeat.
     */
    private fun runloop() {
        var time: Long
        var notifies: TimerQEntry? = null
        var entry: TimerQEntry
        synchronized(this) {
            if (myEvents.isEmpty()) {
                time = 0
            } else {
                entry = myEvents.firstKey()
                time = entry.myWhen - queryTimerMillis() or 1
                /* Avoid 0 since will wait forever */
            }
        }
        synchronized(this) {
            try {
                if (time >= 0) {
                    (this as Object).wait(time)
                }
            } catch (e: Exception) {
                /* No problem - something added or cancelled from queue */
            }
        }
        val now = queryTimerMillis()
        synchronized(this) {
            /* Only do next bunch of stuff if this timer is still running */
            if (myRunning) {
                /* Timer fired, check each element to see if it is time */
                while (!myEvents.isEmpty()) {
                    entry = myEvents.firstKey()
                    if (entry.myWhen <= now) {
                        myEvents.remove(entry)
                        entry.myNext = notifies
                        notifies = entry
                    } else {
                        break
                    }
                }
            }
        }

        /* Enumerate over notifies and notify them */
        while (myRunning && notifies != null) {
            entry = notifies!!
            notifies = notifies!!.myNext
            if (entry.myRepeat) {
                entry.myWhen = entry.myWhen + entry.myDelta
                if (entry.myWhen + entry.myDelta * FUDGE < now) {
                    /* Round up in increments of entry.myDelta to maintain
                       timebase, but myDelta from "now" being rounded
                       up to the timebase */
                    var dist = now - entry.myWhen + entry.myDelta
                    dist = dist / entry.myDelta * entry.myDelta
                    entry.myWhen = entry.myWhen + dist
                }
                insertEntry(entry)
            }
            val target = entry.myTarget
            try {
                target.handleTimeout()
            } catch (e: Exception) {
                exceptionReporter.reportException(e)
            }
        }
    }

    /**
     * Set a timeout event to happen.
     *
     * @param millis Distance into the future for event to happen
     * @param repeat true=>repeat the even every 'millis'; false=>timeout
     * once only
     * @param target Object which will handle the timeout event when it occurs
     */
    fun setTimeout(repeat: Boolean, millis: Long, target: TimerWatcher) {
        synchronized(this) {
            val entry = TimerQEntry(repeat, millis, target, clock)
            insertEntry(entry)
            target.setEvent(entry)
            if (myEvents.firstKey() === entry) {
                wakeup()
            }
        }
    }

    /**
     * Stop the thread.
     */
    fun shutdown() {
        synchronized(this) {
            myRunning = false
            wakeup()
        }
    }

    /**
     * Wake up the sleeping runloop.
     */
    private fun wakeup() {
        try {
            synchronized(this) { (this as Object).notify() }
        } catch (t: Throwable) {
            exceptionReporter.reportException(t, "TimerThread.wakeup() caught exception on notify")
        }
    }

    companion object {
        private const val FUDGE = 5 /* Get > 5 repeating timeouts */
    }

    init {
        priority = MAX_PRIORITY
    }
}
