package org.elkoserver.foundation.json

/**
 * Base class for various classes representing types for optional JSON message
 * parameters.
 *
 * @param present  true=>value is present, false=>it's not
 */
abstract class OptionalParameter internal constructor(val present: Boolean) {

    companion object {
        /**
         * Produce an OptionalParameter value representing an (allowed) missing
         * parameter of some type.
         *
         * @param type  The expected class of the missing parameter.
         *
         * @return the canonical missing value object for 'type'.
         */
        fun missingValue(type: Class<*>): Any? =
                when {
                    !OptionalParameter::class.java.isAssignableFrom(type) -> null
                    type == OptString::class.java -> OptString.theMissingValue
                    type == OptBoolean::class.java -> OptBoolean.theMissingValue
                    type == OptInteger::class.java -> OptInteger.theMissingValue
                    type == OptDouble::class.java -> OptDouble.theMissingValue
                    else -> null
                }
    }
}
