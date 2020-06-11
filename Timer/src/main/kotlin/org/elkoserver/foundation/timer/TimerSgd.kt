@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.timer

import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.gorgel.GorgelExceptionNoticer
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req

class TimerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun clock(): D<java.time.Clock>
        fun baseGorgel(): D<Gorgel>
    }

    val timer by Once { Timer(req(timerThread)) }

    internal val timerThread by Once { TimerThread(req(provided.clock()), req(exceptionReporter)) }
            .init(TimerThread::start)
            .dispose(TimerThread::shutdown)

    val timerThreadExceptionGorgel by Once { req(provided.baseGorgel()).getChild(TimerThread::class, Tag("category", "exception")) }

    val exceptionReporter by Once { ExceptionReporter(GorgelExceptionNoticer(req(timerThreadExceptionGorgel))) }
}
