package org.elkoserver.foundation.run

import org.elkoserver.util.trace.TraceFactory

class RunnerRef(private val traceFactory: TraceFactory) {
    private val runner by lazy { Runner(traceFactory) }

    fun get(): Runner {
        val t = Thread.currentThread()
        return if (t is RunnerThread) {
            t.myRunnable as Runner
        } else {
            runner
        }
    }
}
