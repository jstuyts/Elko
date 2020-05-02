package org.elkoserver.json

import org.elkoserver.json.JSONLiteral.Companion.appendValueString

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
 * @param myStringBuilder  The buffer into which to build the literal string.
 * @param myControl  Encode control determining what flavor of encoding
 *    is being done.
 */
class JSONLiteralArray internal constructor(private val myStringBuilder: StringBuilder, private val myControl: EncodeControl) {

    /** Start of this literal's portion of buffer.  */
    private val myStartPos = myStringBuilder.length

    /** End of this literal's portion of buffer.  */
    private var myEndPos = myStartPos

    /** State of construction.  */
    private var myState = INITIAL

    /** Number of elements successfully added.  */
    private var mySize = 0

    /**
     * Begin a new array literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    @JvmOverloads
    constructor(control: EncodeControl = EncodeControl.forClient) : this(StringBuilder(500), control) {
    }

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
            val arr = JSONLiteralArray(myStringBuilder, myControl)
            for (o in value) {
                arr.addElement(o)
            }
            arr.finish()
        } else if (value != null) {
            val start = myStringBuilder.length
            val starting = myState == INITIAL
            beginElement()
            if (appendValueString(myStringBuilder, value,
                            myControl)) {
                myStringBuilder.setLength(start)
                if (starting) {
                    myState = INITIAL
                }
            } else {
                mySize += 1
            }
        }
    }

    fun addElement(jsonLiteral: JSONLiteral?) {
        addElement(jsonLiteral as Any?)
    }

    fun addElement(jsonLiteralArray: JSONLiteralArray?) {
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
        if (myState != COMPLETE) {
            if (myState == INITIAL) {
                /* Have added first element */
                val STARTED = 1
                myState = STARTED
            } else {
                myStringBuilder.append(", ")
            }
        } else {
            throw Error("attempt to add element to completed array")
        }
    }

    /**
     * Obtain the encode control governing this literal.
     *
     * @return this literal array's encode control.
     */
    fun control(): EncodeControl? {
        return myControl
    }

    /**
     * Finish construction of the literal.
     *
     * @throws Error if you try to finish a literal that is already complete.
     */
    fun finish() {
        if (myState != COMPLETE) {
            myStringBuilder.append(']')
            myState = COMPLETE
            myEndPos = myStringBuilder.length
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
        if (myState != COMPLETE) {
            finish()
        }
        return myStringBuilder.substring(myStartPos, myEndPos)
    }

    /**
     * Obtain the array's element count.
     *
     * @return the number of elements in this array (so far).
     */
    fun size(): Int {
        return mySize
    }

    /**
     * Get the internal string buffer, for collusion with JSONLiteral.
     */
    fun stringBuilder(): StringBuilder {
        return myStringBuilder
    }

    /**
     * Obtain a printable String representation of this literal, in whatever
     * its current state is.
     *
     * @return a printable representation of this literal.
     */
    override fun toString(): String {
        var end = myEndPos
        if (myState != COMPLETE) {
            end = myStringBuilder.length
        }
        return myStringBuilder.substring(myStartPos, end)
    }

    companion object {
        /* The state values */
        private const val INITIAL = 0 /* Have not yet added first element */
        private const val COMPLETE = 2 /* All done */
    }

    init {
        myStringBuilder.append("[")
    }
}