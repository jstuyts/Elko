package org.elkoserver.util.trace

import java.io.PrintWriter

/**
 * Prints a [Throwable] and its backtrace to the specified print
 * stream in way that knows about non-local exceptions.
 *
 * @param problem  The [Throwable] to print a stack trace for.
 * @param out  Print stream to print it on.
 */
internal fun printStackTrace(problem: Throwable, out: PrintWriter) {
    printStackTrace(problem, out, false)
}

/**
 * Prints a [Throwable] and its backtrace to the specified print
 * stream in way that knows about non-local exceptions.
 *
 * @param problem  The [Throwable] to print a stack trace for.
 * @param out  Print stream to print it on
 * @param nonLocal  If <tt>true</tt>, also report the site from which the
 * stack trace is being printed.
 */
internal fun printStackTrace(problem: Throwable, out: PrintWriter,
                             nonLocal: Boolean) {
    out.println("+-vvvv--")
    logStackTrace(problem, out)
    if (nonLocal) {
        logStackTrace(Throwable(), out)
    }
    out.println("+-^^^^--")
}

private fun logStackTrace(problem: Throwable, out: PrintWriter) {
    out.println("+ $problem")
    for (elem in problem.stackTrace) {
        out.println("+    $elem")
    }
    var cause = problem.cause
    while (cause != null) {
        out.println("+ Caused by: $cause")
        for (elem in cause.stackTrace) {
            out.println("+    $elem")
        }
        cause = cause.cause
    }
}