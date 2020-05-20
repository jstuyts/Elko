package org.elkoserver.foundation.server

import org.elkoserver.foundation.net.LoadMonitor
import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import java.time.Clock
import java.util.LinkedList

/**
 * Processing time accumulator to help keep track of system load.
 */
class ServerLoadMonitor constructor(private val timer: Timer, private val clock: Clock, private val myLoadSampleTimeoutTime: Int) : LoadMonitor {
    /** When load tracking began.  */
    private var mySampleStartTime = clock.millis()

    /** Total time that has been spent in processing activity since counting
     * began, in milliseconds.  */
    private var myCumulativeProcessingTime: Long = 0

    /** Objects to be notified of load samples.  */
    private val myLoadWatchers: MutableList<LoadWatcher> = LinkedList()

    /** Timeout for sampling load.  */
    private var myLoadSampleTimeout: Timeout? = null

    /**
     * Take note of some processing time spent.
     *
     * @param timeIncrement  The amount of processing time that was spent, in
     * milliseconds.
     */
    override fun addTime(timeIncrement: Long) {
        myCumulativeProcessingTime += timeIncrement
    }

    /**
     * Add an object to the collection of objects that will be notified when
     * the server samples its load.
     *
     * @param watcher  An object to notify about load samples.
     */
    fun registerLoadWatcher(watcher: LoadWatcher) {
        myLoadWatchers.add(watcher)
        if (myLoadSampleTimeout == null) {
            /* Don't bother sampling until somebody starts watching. */
            scheduleLoadSampling()
        }
    }

    /**
     * Schedule a load sampling event.
     */
    private fun scheduleLoadSampling() {
        myLoadSampleTimeout = timer.after(
                myLoadSampleTimeoutTime.toLong(),
                object : TimeoutNoticer {
                    override fun noticeTimeout() {
                        val factor = sampleLoad()
                        if (myLoadWatchers.size > 0) {
                            for (watcher in myLoadWatchers) {
                                watcher.noteLoadSample(factor)
                            }
                            scheduleLoadSampling()
                        } else {
                            myLoadSampleTimeout = null
                        }
                    }
                })
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server samples its load.
     *
     * @param watcher  The object to stop notifying about load samples.
     */
    fun unregisterLoadWatcher(watcher: LoadWatcher) {
        myLoadWatchers.remove(watcher)
    }

    /**
     * Compute the load, defined as the ratio between the cumulative processing
     * time spent and the elapsed clock time since the last time the load was
     * sampled.  Sampling the load resets the start time to the sample time and
     * zeroes the cumulative processing time accumulator.
     *
     * @return the current load estimate, as described above.
     */
    private fun sampleLoad(): Double {
        val clockTime = clock.millis() - mySampleStartTime
        var loadFactor = 0.0
        if (clockTime > 0) {
            loadFactor = myCumulativeProcessingTime.toDouble() /
                    clockTime.toDouble()
        }
        mySampleStartTime = clock.millis()
        myCumulativeProcessingTime = 0
        return loadFactor
    }

    companion object {
        /** Default value for interval between load samples, in seconds.  */
        const val DEFAULT_LOAD_SAMPLE_TIMEOUT = 30
    }
}
