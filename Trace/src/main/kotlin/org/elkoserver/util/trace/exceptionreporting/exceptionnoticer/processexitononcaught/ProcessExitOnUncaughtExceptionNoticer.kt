package org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.processexitononcaught

import org.elkoserver.util.trace.exceptionreporting.ExceptionNoticer
import kotlin.system.exitProcess

@Deprecated(message = "For compatibility with (deleted) TraceExceptionNoticer. Switch to graceful handling of uncaught throwables")
class ProcessExitOnUncaughtExceptionNoticer(private val delegatee: ExceptionNoticer) : ExceptionNoticer by delegatee {
    override fun noticeUncaughtException(message: String, throwable: Throwable) {
        delegatee.noticeUncaughtException(message, throwable)
        // This is for compatibility with TraceExceptionNoticer, but this needs to gracefully shut down the process,
        //  restart the service, ...
        exitProcess(1)
    }
}
