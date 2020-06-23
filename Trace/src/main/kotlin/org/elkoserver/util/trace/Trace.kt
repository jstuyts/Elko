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
        debug = false
        event = false
        when (myThreshold) {
            Level.DEBUG -> {
                debug = true
                event = true
            }
            Level.EVENT -> {
                event = true
            }
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
     */
    private fun recordEventInfoMessage(message: String) {
        val traceMessage: TraceMessage = TraceMessageInfo(mySubsystem, Level.EVENT, message, clock)
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
        recordDebugMessage(message, Level.ERROR, null)
    }

    /**
     * Output a log message at <tt>ERROR</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    fun errorm(message: String, obj: Any) {
        recordDebugMessage(message, Level.ERROR, obj)
    }

    /**
     * Output an informational log message at <tt>EVENT</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun eventi(message: String) {
        if (event) recordEventInfoMessage(message)
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
     * Output a log message at <tt>WARNING</tt> level.
     *
     * @param message  The message to write to the log.
     */
    fun warningm(message: String) {
        recordDebugMessage(message, Level.WARNING, null)
    }

    init {
        updateThresholdFlags()
    }
}
