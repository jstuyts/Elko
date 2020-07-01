package org.elkoserver.util.trace

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class TraceFactory internal constructor(private val controller: TraceController, private val clock: Clock) {
    /**
     * The Trace objects for subsystems being traced, indexed by subsystem
     * name.
     */
    private val theTraces: ConcurrentMap<String, Trace> = ConcurrentHashMap()

    /**
     * Obtain the Trace object for a given subsystem.  The Trace object will
     * be manufactured if it does not already exist.
     *
     * @param subsystem  The name of the subsystem of interest.
     *
     * @return a Trace object for the given subsystem.
     */
    fun trace(subsystem: String): Trace {
        val key = subsystem.toLowerCase()
        return theTraces.computeIfAbsent(key) { Trace(subsystem, controller.theDefaultThreshold, controller.acceptor, this, clock) }
    }

    internal fun subTrace(baseTrace: Trace, subSubsystem: String): Trace {
        val result = trace("${baseTrace.mySubsystem}-$subSubsystem")
        if (result.myThresholdIsDefaulted && !baseTrace.myThresholdIsDefaulted) {
            result.setThreshold(baseTrace.myThreshold)
        }
        return result
    }
}
