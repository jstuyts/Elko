package org.elkoserver.json

import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.JsonLiteral.Companion.appendValueString

/**
 * A literal JSON string, representing an array, undergoing incremental
 * construction.
 *
 * Users of this class should call the constructor to begin creation of the
 * literal, incrementally add to it using the various
 * [addElement()][.addElement] methods, then finally complete it by
 * calling the [.finish] method.  After the literal is completed, it may
 * be used as another literal's parameter value.
 *
 * @param stringBuilder  The buffer into which to build the literal string.
 * @param control  Encode control determining what flavor of encoding
 *    is being done.
 */
class JsonLiteralArray internal constructor(internal val stringBuilder: StringBuilder, private val control: EncodeControl) {

    /** Start of this literal's portion of buffer.  */
    private val myStartPos = stringBuilder.length

    /** End of this literal's portion of buffer.  */
    private var myEndPos = myStartPos

    /** State of construction.  */
    private var myState = JsonLiteralArrayState.INITIAL

    /** Number of elements successfully added.  */
    var size = 0
        private set

    /**
     * Begin a new array literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    constructor(control: EncodeControl = ForClientEncodeControl) : this(StringBuilder(500), control)

    /**
     * Add an element to the incomplete array literal. Note that any element
     * value that encodes to the Java value null will be ignored (i.e., not
     * added to the literal).
     *
     * @param value  The element value.
     *
     * @throws Error if you try to add an element to literal that is already
     * complete.
     */
    fun addElement(value: Any?) {
        if (value is Array<*>) {
            beginElement()
            val arr = JsonLiteralArray(stringBuilder, control)
            for (o in value) {
                arr.addElement(o)
            }
            arr.finish()
        } else if (value != null) {
            val start = stringBuilder.length
            val starting = myState == JsonLiteralArrayState.INITIAL
            beginElement()
            if (appendValueString(stringBuilder, value, control)) {
                stringBuilder.setLength(start)
                if (starting) {
                    myState = JsonLiteralArrayState.INITIAL
                }
            } else {
                size += 1
            }
        }
    }

    fun addElement(jsonLiteral: JsonLiteral?) {
        addElement(jsonLiteral as Any?)
    }

    fun addElement(jsonLiteralArray: JsonLiteralArray?) {
        addElement(jsonLiteralArray as Any?)
    }

    fun addElement(bool: Boolean?) {
        addElement(bool as Any?)
    }

    fun addElement(number: Number?) {
        addElement(number as Any?)
    }

    fun addElement(string: String?) {
        addElement(string as Any?)
    }

    /**
     * Add a reference element to an incomplete array.
     *
     * @param value  The ([Referenceable]) element value.
     */
    fun addElement(value: Referenceable) {
        addElement(value.ref())
    }

    /**
     * Prepend any necessary punctuation upon starting a new element, and
     * update the state of construction accordingly.
     */
    private fun beginElement() {
        if (myState != JsonLiteralArrayState.COMPLETE) {
            if (myState == JsonLiteralArrayState.INITIAL) {
                /* Have added first element */
                myState = JsonLiteralArrayState.STARTED
            } else {
                stringBuilder.append(", ")
            }
        } else {
            throw Error("attempt to add element to completed array")
        }
    }

    /**
     * Finish construction of the literal.
     *
     * @throws Error if you try to finish a literal that is already complete.
     */
    fun finish() {
        if (myState != JsonLiteralArrayState.COMPLETE) {
            stringBuilder.append(']')
            myState = JsonLiteralArrayState.COMPLETE
            myEndPos = stringBuilder.length
        } else {
            throw Error("attempt to finish already completed array")
        }
    }

    /**
     * Obtain a string representation of this literal suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this literal.
     *
     * @throws Error if the literal is not finished.
     */
    fun sendableString(): String {
        if (myState != JsonLiteralArrayState.COMPLETE) {
            finish()
        }
        return stringBuilder.substring(myStartPos, myEndPos)
    }

    /**
     * Obtain a printable String representation of this literal, in whatever
     * its current state is.
     *
     * @return a printable representation of this literal.
     */
    override fun toString(): String {
        var end = myEndPos
        if (myState != JsonLiteralArrayState.COMPLETE) {
            end = stringBuilder.length
        }
        return stringBuilder.substring(myStartPos, end)
    }

    init {
        stringBuilder.append("[")
    }

    companion object {
        /**
         * Convenience function to encode an object in a single-element array.
         *
         * @param elem  The object to put in the array.
         *
         * @return a JSONLiteralArray containing the encoded 'elem'.
         */
        fun singleElementArray(elem: JsonLiteral) =
                JsonLiteralArray().apply {
                    addElement(elem)
                    finish()
                }
    }
}

private enum class JsonLiteralArrayState {
    INITIAL,
    STARTED,
    COMPLETE
}
