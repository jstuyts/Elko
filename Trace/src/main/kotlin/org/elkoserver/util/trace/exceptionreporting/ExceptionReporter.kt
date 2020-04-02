package org.elkoserver.util.trace.exceptionreporting

/**
 * A collection of static methods for doing useful things with exceptions.
 */
class ExceptionReporter(private var theNoticer: ExceptionNoticer) {

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an [ExceptionNoticer] has been registered, by
     * informing the [ExceptionNoticer].  The exception is considered
     * local.
     *
     * @param problem  The [Throwable] to report.
     */
    fun reportException(problem: Throwable) {
        reportException(problem, "", false)
    }

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an [ExceptionNoticer] has been registered, by
     * informing the [ExceptionNoticer].  The exception is considered
     * local.
     *
     * @param problem  The [Throwable] to report.
     * @param msg  Error message to accompany the report.
     */
    fun reportException(problem: Throwable, msg: String) {
        reportException(problem, msg, false)
    }

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an [ExceptionNoticer] has been registered, by
     * informing the [ExceptionNoticer].
     *
     * @param problem  The [Throwable] to report.
     * @param msg  Error message to accompany the report.
     * @param nonLocal  If <tt>true</tt>, also report the site from which the
     * stack trace is being printed.
     */
    private fun reportException(problem: Throwable, msg: String, nonLocal: Boolean) {
        val theActualProblem = if (nonLocal) Throwable(problem) else problem
        theNoticer.noticeReportedException(msg, theActualProblem)
    }

    /**
     * Report an uncaught exception.
     *
     * @param thread  The thread this happened in.
     * @param problem  The exception that wasn't caught.
     */
    fun uncaughtException(thread: Thread, problem: Throwable) {
        if (problem !is ThreadDeath) {
            val msg = "Uncaught exception in thread " + thread.name
            reportException(problem, msg, true)
            theNoticer.noticeUncaughtException(msg, problem)
        }
    }
}
