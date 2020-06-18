package org.elkoserver.util.trace

import java.time.Clock

/**
 * This class provides output to the server log on behalf of a particular
 * server subsystem.  A number of frequently used trace subsystem objects are
 * available as static variables on this class.  Users can also create
 * additional trace objects as needed for their own purposes.
 *
 * A collection of trace priority thresholds, represented by a group of boolean
 * variables, control which trace messages are actually output.  These
 * variables can be tested by a user of the trace code to quickly decide
 * whether to call a trace method (thus helping avoid the costs of procedure
 * call and string manipulation in cases where no output would be generated
 * anyway).
 */
class Trace internal constructor(internal val mySubsystem: String, internal var myThreshold: Level, private val myAcceptor: TraceMessageAcceptor, private val factory: TraceFactory, private val clock: Clock) {

    /**
     * Flag to control tracing of error messages.  Error messages report on
     * some internal error.  They don't necessarily lead to the system
     * stopping, but they might.  Error messages are always logged.
     */
    var error = false

    /**
     * Flag to control tracing of warning messages.  Warning messages are not
     * as serious as errors, but they're signs of something odd.
     */
    var warning = false

    /**
     * Flag to control tracing of usage messages.  Usage messages are used to
     * answer the question "who did what up to the point the bug appeared?"
     * They are also used to collect higher-level usability information.
     */
    var usage = false

    /**
     * Flag to control tracing of event messages.  Event messages describe the
     * major actions the system takes in response to user actions.  The
     * distinction between this category and debug is fuzzy, especially since
     * debug is already used for many messages of this type.  However, it can
     * be used to log specific user actions for usability testing, and to log
     * information for testers.
     */
    var event = false

    /**
     * Flag to control tracing of debug messages.  Debug messages provide more
     * detail for people who want to delve into what's going on, probably to
     * figure out a bug.
     */
    var debug = false

    /**
     * Flag to control tracing of verbose messages.  Verbose messages provide
     * even more detail than debug.  They're probably mainly used when first
     * getting code to work.
     */
    var verbose = false

    /** Flag that the threshold is defaulted.  */
    internal var myThresholdIsDefaulted = true

    /**
     * Obtain a Trace object based on another Trace object.  The new trace
     * object will acquire its initial threshold settings based on its parent.
     *
     * @param subSubsystem  A name tag that will be appended to this trace
     * object's subsystem name.
     *
     * @return a a Trace object derived from this trace object.
     */
    fun subTrace(subSubsystem: String): Trace = factory.subTrace(this, subSubsystem)

    /**
     * Set the logging threshold.
     *
     * @param threshold  The new threshold value.
     */
    fun setThreshold(threshold: Level) {
        if (myThreshold !== threshold) {
            myThreshold = threshold
            updateThresholdFlags()
        }
        myThresholdIsDefaulted = false
    }

    /**
     * Take note of the tracing threshold being changed.  This will set the
     * various control booleans based on the new threshold value.
     */
    private fun updateThresholdFlags() {
        verbose = false
        debug = false
        event = false
        usage = false
        warning = false
        error = false
        when (myThreshold) {
            Level.VERBOSE -> {
                verbose = true
                debug = true
                event = true
                usage = true
                warning = true
                error = true
            }
            Level.DEBUG -> {
                debug = true
                event = true
                usage = true
                warning = true
                error = true
            }
            Level.EVENT -> {
                event = true
                usage = true
                warning = true
                error = true
            }
            Level.USAGE -> {
                usage = true
                warning = true
                error = true
            }
            Level.WORLD -> {
                warning = true
                error = true
            }
            Level.WARNING -> {
                warning = true
                error = true
            }
            Level.ERROR -> error = true
            else -> assert(false) {
                "bad case in updateThresholdFlags: $myThreshold"
            }
        }
    }

    /**
     * Actually manufacture a debug-style trace message and put it into the
     * log.
     *
     * @param message  The message string
     * @param level  Trace level at which it is being output
     * @param obj  Arbitrary annotation object to go with the message (usually
     * but not necessarily an exception).
     */
    private fun recordDebugMessage(message: String, level: Level, obj: Any?) {
        val frame = try {
            Exception().stackTrace[2]
        } catch (e: Throwable) {
            null
        }
        val traceMessage: TraceMessage = TraceMessageDebug(mySubsystem, level, frame, message, obj, clock)
        myAcceptor.accept(traceMessage)
    }

    /**
     * Actually manufacture a info-style trace message and put it into the
     * log.
     *
     * @param message  The message string
     * @param level  Trace level at which it is being output
     */
    private fun recordInfoMessage(message: String, level: Level) {
        val traceMessage: TraceMessage = TraceMessageInfo(mySubsystem, level, message, clock)
        myAcceptor.accept(traceMessage)
    }

    /**
     * Output a log message at <tt>DEBUG</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun debugm(message: String) {
        if (debug) recordDebugMessage(message, Level.DEBUG, null)
    }

    /**
     * Output a log message at <tt>DEBUG</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    fun debugm(message: String, obj: Any?) {
        if (debug) recordDebugMessage(message, Level.DEBUG, obj)
    }

    /**
     * Output a log message at <tt>ERROR</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun errorm(message: String) {
        if (error) recordDebugMessage(message, Level.ERROR, null)
    }

    /**
     * Output a log message at <tt>ERROR</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    fun errorm(message: String, obj: Any) {
        if (error) recordDebugMessage(message, Level.ERROR, obj)
    }

    /**
     * Log an exception event at <tt>ERROR</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    fun errorReportException(th: Throwable?, message: String) {
        if (error) recordDebugMessage(message, Level.ERROR, th)
    }

    /**
     * Output an informational log message at <tt>EVENT</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun eventi(message: String) {
        if (event) recordInfoMessage(message, Level.EVENT)
    }

    /**
     * Output a log message at <tt>EVENT</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun eventm(message: String) {
        if (event) recordDebugMessage(message, Level.EVENT, null)
    }

    /**
     * Output an informational log message at <tt>USAGE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun usagei(message: String) {
        if (usage) recordInfoMessage(message, Level.USAGE)
    }

    /**
     * Output a log message at <tt>USAGE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun usagem(message: String) {
        if (usage) recordDebugMessage(message, Level.USAGE, null)
    }

    /**
     * Output a log message at <tt>VERBOSE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun verbosem(message: String) {
        if (verbose) recordDebugMessage(message, Level.VERBOSE, null)
    }

    /**
     * Output a log message at <tt>WARNING</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun warningm(message: String) {
        if (warning) recordDebugMessage(message, Level.WARNING, null)
    }

    init {
        updateThresholdFlags()
    }
}
