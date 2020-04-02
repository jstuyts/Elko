package org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.trace

import org.elkoserver.util.trace.exceptionreporting.ExceptionNoticer
import org.elkoserver.util.trace.Trace

/**
 * Class for exception handling of trace.
 */
class TraceExceptionNoticer(private val trace: Trace) : ExceptionNoticer {
    override fun noticeReportedException(msg: String, t: Throwable) {
        trace.errorm(msg, t)
    }

    override fun noticeUncaughtException(msg: String, t: Throwable) {
        trace.fatalError(msg, t)
    }
}
