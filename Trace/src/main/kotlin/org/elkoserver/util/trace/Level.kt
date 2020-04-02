package org.elkoserver.util.trace

/**
 * The different trace thresholds.  See the Trace class for documentation.
 * There is space between the levels for expansion.  If you add or delete
 * a level, you must change Trace.java to add new methods and variables.
 */
enum class Level(val terseCode: String) {
    /** "Notice" level (not thresholded)  */
    NOTICE("NTC"),

    /** "Error" level trace threshold  */ /* Always on */
    ERROR("ERR"),

    /** "Warning" level trace threshold  */
    WARNING("WRN"),

    /** "World" level trace threshold  */
    WORLD("WLD"),

    /** "Usage" level trace threshold  */
    USAGE("USE"),

    /** "Event" level trace threshold  */
    EVENT("EVN"),

    /** "Message" level trace threshold.  Essentially the same as "Event", but
     * flagged differently in log entries.  */
    MESSAGE("MSG"),

    /** "Debug" level trace threshold  */
    DEBUG("DBG"),

    /** "Verbose" level trace threshold  */
    VERBOSE("VRB");
}
