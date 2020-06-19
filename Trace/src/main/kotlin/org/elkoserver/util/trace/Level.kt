package org.elkoserver.util.trace

/**
 * The different trace thresholds.  See the Trace class for documentation.
 * There is space between the levels for expansion.  If you add or delete
 * a level, you must change Trace.java to add new methods and variables.
 */
enum class Level(val terseCode: String) {
    /** "Error" level trace threshold  */ /* Always on */
    ERROR("ERR"),

    /** "Warning" level trace threshold  */
    WARNING("WRN"),

    /** "Event" level trace threshold  */
    EVENT("EVN"),

    /** "Debug" level trace threshold  */
    DEBUG("DBG"),
}
