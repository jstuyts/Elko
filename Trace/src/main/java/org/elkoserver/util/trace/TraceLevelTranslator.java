package org.elkoserver.util.trace;

import static java.util.Locale.ENGLISH;

/**
 * Translate numerical trace levels into strings and vice versa.
 */
class TraceLevelTranslator {

    /**
     * Convert a string into one of the numeric trace levels.
     *
     * @param level  The string to be converted
     *
     * @return the level number named by 'level'
     *
     * @throws IllegalArgumentException if the string is not recognized.
     */
    static Trace.Level toLevel(String level) throws IllegalArgumentException {
        String lowerCaseLevel = level.toLowerCase(ENGLISH);
        switch (lowerCaseLevel) {
            case "error":
                return Trace.Level.ERROR;
            case "warning":
                return Trace.Level.WARNING;
            case "world":
                return Trace.Level.WORLD;
            case "usage":
                return Trace.Level.USAGE;
            case "event":
                return Trace.Level.EVENT;
            case "debug":
                return Trace.Level.DEBUG;
            case "verbose":
                return Trace.Level.VERBOSE;
            default:
                String problem = "Incorrect tracing threshold '" + lowerCaseLevel + "'";
                Trace.trace.errori(problem);
                throw new IllegalArgumentException(problem);
        }
    }
}
