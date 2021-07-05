@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.timer.timerthread

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.gorgel.GorgelExceptionNoticer
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
import org.ooverkommelig.Definition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req

class TimerThreadTimerSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(configuration) {
    interface Provided {
        fun clock(): D<java.time.Clock>
        fun baseGorgel(): D<Gorgel>
    }

    val timer: Definition<Timer> by Once { TimerThreadTimer(req(timerThread)) }

    internal val timerThread by Once { TimerThread(req(provided.clock()), req(exceptionReporter)) }
            .init(TimerThread::start)
            .dispose(TimerThread::shutDown)

    internal val timerThreadExceptionGorgel: Definition<Gorgel> by Once { req(provided.baseGorgel()).getChild(TimerThread::class, Tag("category", "exception")) }

    internal val exceptionReporter: Definition<ExceptionReporter> by Once { ExceptionReporter(GorgelExceptionNoticer(req(timerThreadExceptionGorgel))) }
}
