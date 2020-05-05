@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.timer

import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.trace.TraceExceptionNoticer
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req

class TimerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun clock(): D<java.time.Clock>
        fun traceFactory(): D<TraceFactory>
    }
    val timer by Once { Timer(req(timerThread)) }

    internal val timerThread by Once { TimerThread(req(provided.clock()), req(exceptionReporter)) }
            .init { it.start() }

    val exceptionTrace by Once { req(provided.traceFactory()).exception }

    val exceptionReporter by Once { ExceptionReporter(TraceExceptionNoticer(req(exceptionTrace)))  }
}
