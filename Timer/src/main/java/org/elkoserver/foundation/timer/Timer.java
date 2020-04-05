package org.elkoserver.foundation.timer;

import org.elkoserver.util.trace.TraceFactory;

/**
 * The master control object for scheduling timed events using timeouts and
 * clocks.  One-time events (controlled by {@link Timeout} objects) may be
 * scheduled by calling either of the {@link #after after()} methods.
 * Recurring events (controlled by {@link Clock} objects) may be scheduled by
 * calling either of the {@link #every every()} methods.<p>
 *
 * Event notification is guaranteed to be prompt but not immediate: the event
 * handler will be invoked no sooner than scheduled and as soon thereafter as
 * possible, but no guarantees are offered that somewhat more time will not
 * have passed than was requested.  In particular, while the scheduling API
 * lets you specify times with millisecond precision, millisecond accuracy in
 * practice should not be assumed.
 */
public class Timer {

    /** The timer thread */
    private TimerThread myThread;

    /**
     * Private constructor.  Just start the timer thread.
     */
    public Timer(TraceFactory traceFactory, java.time.Clock clock) {
        myThread = new TimerThread(traceFactory, clock);
        myThread.start();
    }

    /**
     * Sets a timeout for the specified number of milliseconds.  After the
     * timer expires, <code>target</code>'s {@link TimeoutNoticer#noticeTimeout
     * noticeTimeout()} method is called.  Notification is always asynchronous.
     *
     * @param millis  How long to wait until timing out.
     * @param target  Object to be informed when the time comes.
     *
     * @return a timeout object that can be used to cancel or identify the
     *   timeout.
     *
     * @see TimeoutNoticer
     */
    public Timeout after(long millis, TimeoutNoticer target) {
        Timeout newTimeout = new Timeout(myThread, target);
        myThread.setTimeout(false, millis, newTimeout);
        return newTimeout;
    }

    /**
     * Creates a new clock.  The new clock begins life stopped with its tick
     * count at zero (start the clock ticking by calling its {@link Clock#start
     * start()} method).  Clock ticks are always asynchronous.
     *
     * @param resolution  The clock tick interval.
     * @param target  Object to be sent tick notifications.
     *
     * @return a new clock object according to the given parameters.
     *
     * @see TickNoticer
     */
    public Clock every(long resolution, TickNoticer target) {
        return new Clock(myThread, resolution, target);
    }
}
