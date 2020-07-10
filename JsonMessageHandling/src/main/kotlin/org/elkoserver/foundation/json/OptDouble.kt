package org.elkoserver.foundation.json

/**
 * An optional JSON message parameter of type double.
 */
class OptDouble : OptionalParameter {
    /** The actual double value  */
    private var myValue = 0.0

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    constructor(value: Double) : super(true) {
        myValue = value
    }

    /**
     * Constructor (value absent).
     */
    private constructor() : super(false)

    /**
     * Get the double value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (double) value.
     *
     * @throws Error if the value is not present.
     */
    fun value(): Double =
            if (present) {
                myValue
            } else {
                throw Error("extraction of value from non-present OptDouble")
            }

    /**
     * Get the double value of this parameter, or a default value if the value
     * is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the double value of this parameter if present, or the value of
     * 'defaultValue' if not present.
     */
    fun value(defaultValue: Double): Double =
            if (present) {
                myValue
            } else {
                defaultValue
            }

    companion object {
        /** Singleton instance of OptDouble with the value not present.  */
        val theMissingValue: OptDouble = OptDouble()
    }
}
