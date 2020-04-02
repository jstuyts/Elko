package org.elkoserver.util.trace

import java.util.Locale.ENGLISH

internal fun stringToTraceLevel(possibleLevel: String): Level {
    return when (possibleLevel.toUpperCase(ENGLISH)) {
        "ERROR" -> Level.ERROR
        "WARNING" -> Level.WARNING
        "WORLD" -> Level.WORLD
        "USAGE" -> Level.USAGE
        "EVENT" -> Level.EVENT
        "DEBUG" -> Level.DEBUG
        "VERBOSE" -> Level.VERBOSE
        else -> {
            throw IllegalArgumentException("Incorrect tracing threshold '$possibleLevel'")
        }
    }
}
