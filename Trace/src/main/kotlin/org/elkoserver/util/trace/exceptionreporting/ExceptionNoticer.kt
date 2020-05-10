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
     * @param message  The message that accompanied the exception report.
     * @param throwable  The actual exception itself.
     */
    fun noticeReportedException(message: String, throwable: Throwable)

    /**
     * Notification of an uncaught exception.
     *
     * @param message  Message describing the circumstances.
     * @param throwable  The actual exception itself.
     */
    fun noticeUncaughtException(message: String, throwable: Throwable)
}
