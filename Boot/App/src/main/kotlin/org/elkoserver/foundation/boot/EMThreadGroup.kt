package org.elkoserver.foundation.boot

import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter

/**
 * Thread group for all server application threads to run in.  Will punt
 * all uncaught exceptions to the [ExceptionReporter] class.
 *
 * @param name The name for the new thread group.
 */
internal class EMThreadGroup internal constructor(name: String, private val myExceptionReporter: ExceptionReporter) : ThreadGroup(name) {

    /**
     * Handle uncaught exceptions by giving them to the
     * [ExceptionReporter].
     *
     * @param thread  The thread in which the exception was thrown (and not
     * caught).
     * @param ex  The exception itself.
     */
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        myExceptionReporter.uncaughtException(thread, ex)
    }
}
