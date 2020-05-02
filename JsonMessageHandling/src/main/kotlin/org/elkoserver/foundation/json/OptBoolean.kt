package org.elkoserver.foundation.json

/**
 * An optional JSON message parameter of type boolean.
 */
class OptBoolean : OptionalParameter {
    /** The actual boolean value  */
    private var myValue = false

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter
     */
    constructor(value: Boolean) : super(true) {
        myValue = value
    }

    /**
     * Constructor (value absent).
     */
    private constructor() : super(false)

    /**
     * Get the boolean value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (boolean) value.
     *
     * @throws Error if the value is not present.
     */
    fun value() =
            if (isPresent) {
                myValue
            } else {
                throw Error("extraction of value from non-present OptBoolean")
            }

    /**
     * Get the boolean value of this parameter, or a default value if the value
     * is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the boolean value of this parameter if present, or the value of
     * 'defaultValue' if not present.
     */
    fun value(defaultValue: Boolean) =
            if (isPresent) {
                myValue
            } else {
                defaultValue
            }

    companion object {
        /** Singleton instance of OptBoolean with the value not present.  */
        val theMissingValue = OptBoolean()
    }
}
