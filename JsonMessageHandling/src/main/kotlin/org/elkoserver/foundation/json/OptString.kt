package org.elkoserver.foundation.json

/**
 * An optional JSON message parameter of type [String].
 */
class OptString : OptionalParameter {
    /** The actual String value  */
    private lateinit var myValue: String

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    constructor(value: String) : super(true) {
        myValue = value
    }

    /**
     * Constructor (value absent).
     */
    private constructor() : super(false)

    /**
     * Get the [String] value of the parameter.  It is an error if the
     * value is absent.
     *
     * @return the ([String]) value.
     *
     * @throws Error if the value is not present.
     */
    fun value() =
            if (isPresent) {
                myValue
            } else {
                throw Error("extraction of value from non-present OptString")
            }

    /**
     * Get the [String] value of this parameter, or a default value if
     * the value is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the [String] value of this parameter if present, or the
     * value of 'defaultValue' if not present.
     */
    fun <TDefault : String?> value(defaultValue: TDefault): TDefault =
            if (isPresent) {
                myValue as TDefault
            } else {
                defaultValue
            }

    companion object {
        /** Singleton instance of OptString with the value not present.  */
        val theMissingValue = OptString()
    }
}
