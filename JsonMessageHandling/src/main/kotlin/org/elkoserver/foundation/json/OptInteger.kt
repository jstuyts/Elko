package org.elkoserver.foundation.json

/**
 * An optional JSON message parameter of type int.
 */
class OptInteger : OptionalParameter {
    /** The actual int value  */
    private var myValue = 0

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    constructor(value: Int) : super(true) {
        myValue = value
    }

    /**
     * Constructor (value absent).
     */
    private constructor() : super(false)

    /**
     * Get the int value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (int) value.
     *
     * @throws Error if the value is not present.
     */
    fun value() =
            if (present) {
                myValue
            } else {
                throw Error("extraction of value from non-present OptInteger")
            }

    /**
     * Get the int value of this parameter, or a default value if the value is
     * absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the int value of this parameter if present, or the value of
     * 'defaultValue' if not present.
     */
    fun value(defaultValue: Int) =
            if (present) {
                myValue
            } else {
                defaultValue
            }

    companion object {
        /** Singleton instance of OptInteger with the value not present.  */
        val theMissingValue = OptInteger()
    }
}
