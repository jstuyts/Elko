package org.elkoserver.util.trace.exceptionreporting

/**
 * Interface to be implemented by the entity that is to be notified of all
 * exceptions reported to [ExceptionReporter] or which are uncaught.
 *
 * @see ExceptionReporter
 */
interface ExceptionNoticer {
    /**
     * Notification of a reported exception.
     *
     * @param msg  The message that accompanied the exception report.
     * @param t  The actual exception itself.
     */
    fun noticeReportedException(msg: String, t: Throwable)

    /**
     * Notification of an uncaught exception.
     *
     * @param msg  Message describing the circumstances.
     * @param t  The actual exception itself.
     */
    fun noticeUncaughtException(msg: String, t: Throwable)
}
