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
        String upperCaseLevel = level.toUpperCase(ENGLISH);
        switch (upperCaseLevel) {
            case "ERROR":
                return Trace.Level.ERROR;
            case "WARNING":
                return Trace.Level.WARNING;
            case "WORLD":
                return Trace.Level.WORLD;
            case "USAGE":
                return Trace.Level.USAGE;
            case "EVENT":
                return Trace.Level.EVENT;
            case "DEBUG":
                return Trace.Level.DEBUG;
            case "VERBOSE":
                return Trace.Level.VERBOSE;
            default:
                String problem = "Incorrect tracing threshold '" + upperCaseLevel + "'";
                Trace.trace.errori(problem);
                throw new IllegalArgumentException(problem);
        }
    }
}
