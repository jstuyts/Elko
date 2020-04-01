package org.elkoserver.util.trace;

import java.io.PrintStream;

/**
 * A collection of static methods for doing useful things with exceptions.
 */
public class ExceptionManager
{
    private static ExceptionNoticer theNoticer = null;

    /**
     * Suppress the Miranda constructor.
     */
    private ExceptionManager() { }


    /**
     * Wrap a {@link Throwable} in a {@link RuntimeException}.<p>
     *
     * Wraps <tt>problem</tt> if necessary so that the caller can do a
     * <pre>
     *     throw ExceptionMgr.asSafe(problem); </pre>
     *
     * without having to declare any new "throws" cases.  The caller does the
     * throw rather than this method so that the Java compiler will have better
     * control flow information.
     *
     * @param problem  The {@link Throwable} to wrap
     */
    public static RuntimeException asSafe(Throwable problem) {
        if (problem instanceof RuntimeException) {
            return (RuntimeException) problem;
        }
        throw new RuntimeException(problem);
    }

    /**
     * Prints a {@link Throwable} and its backtrace to the specified print
     * stream in way that knows about non-local exceptions.
     *
     * @param problem  The {@link Throwable} to print a stack trace for.
     * @param out  Print stream to print it on.
     */
    public static void printStackTrace(Throwable problem, PrintStream out) {
        printStackTrace(problem, out, false);
    }

    /**
     * Prints a {@link Throwable} and its backtrace to the specified print
     * stream in way that knows about non-local exceptions.
     *
     * @param problem  The {@link Throwable} to print a stack trace for.
     * @param out  Print stream to print it on
     * @param nonLocal  If <tt>true</tt>, also report the site from which the
     *    stack trace is being printed.
     */
    private static void printStackTrace(Throwable problem, PrintStream out,
                                        boolean nonLocal)
    {
        out.println("+-vvvv--");
        logStackTrace(problem, out);
        if (nonLocal) {
            logStackTrace(new ReportedByException(), out);
        }
        out.println("+-^^^^--");
    }

    private static void logStackTrace(Throwable problem, PrintStream out) {
        out.println("+ " + problem);
        for (StackTraceElement elem : problem.getStackTrace()) {
            out.println("+    " + elem);
        }
        Throwable cause = problem.getCause();
        while (cause != null) {
            out.println("+ Caused by: " + cause);
            for (StackTraceElement elem : cause.getStackTrace()) {
                out.println("+    " + elem);
            }
            cause = cause.getCause();
        }
    }

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an {@link ExceptionNoticer} has been registered, by
     * informing the {@link ExceptionNoticer}.  The exception is considered
     * local.
     *
     * @param problem  The {@link Throwable} to report.
     */
    public static void reportException(Throwable problem) {
        reportException(problem, "", false);
    }

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an {@link ExceptionNoticer} has been registered, by
     * informing the {@link ExceptionNoticer}.  The exception is considered
     * local.
     *
     * @param problem  The {@link Throwable} to report.
     * @param msg  Error message to accompany the report.
     */
    public static void reportException(Throwable problem, String msg) {
        reportException(problem, msg, false);
    }

    /**
     * Handle an exception, either by printing its stack trace to the standard
     * error stream or, if an {@link ExceptionNoticer} has been registered, by
     * informing the {@link ExceptionNoticer}.
     *
     * @param problem  The {@link Throwable} to report.
     * @param msg  Error message to accompany the report.
     * @param nonLocal  If <tt>true</tt>, also report the site from which the
     *    stack trace is being printed.
     */
    private static void reportException(Throwable problem, String msg,
                                        boolean nonLocal)
    {
        if (theNoticer != null) {
            if (nonLocal) {
                problem = new ReportedByException(problem);
            }
            theNoticer.noticeReportedException(msg, problem);
        } else {
            System.err.println(msg);
            printStackTrace(problem, System.err, nonLocal);
        }
    }

    /**
     * Register an {@link ExceptionNoticer} to be informed whenever one of the
     * <tt>reportException()</tt> methods is called.
     *
     * @param noticer  The noticer to call.
     */
    public static void setExceptionNoticer(ExceptionNoticer noticer) {
        if (theNoticer != null) {
            throw new SecurityException(
                "cannot reset ExceptionManager exception noticer");
        }
        theNoticer = noticer;
    }

    /**
     * Report an uncaught exception.
     *
     * @param thread  The thread this happened in.
     * @param problem  The exception that wasn't caught.
     */
    public static void uncaughtException(Thread thread, Throwable problem) {
        if (!(problem instanceof ThreadDeath)) {
            String msg = "Uncaught exception in thread " + thread.getName();
            reportException(problem, msg, true);
            if (theNoticer != null) {
                theNoticer.noticeUncaughtException(msg, problem);
            }
        }
    }


    private static class ReportedByException extends RuntimeException {
        ReportedByException() {
        }

        ReportedByException(Throwable t) {
            super(t);
        }
    }
}
