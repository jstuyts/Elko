package org.elkoserver.json

import org.elkoserver.json.JsonArraySerialization.encodeLiteral

/**
 * A literal JSON string, representing either a message or an object,
 * undergoing incremental construction.
 *
 * Users of this class should call one of the constructors to begin creation
 * of the literal, incrementally add to it using the various
 * [addParameter()][.addParameter] methods, then finally complete it by
 * calling the [.finish] method.  After the literal is completed, it may
 * be used as another literal's parameter value, or its string form can be
 * extracted by calling [.sendableString].
 *
 * @param myStringBuilder  The buffer into which to build the literal string.
 * @param myControl  Encode control determining what flavor of encoding
 *    is being done.
 */
class JSONLiteral internal constructor(private val myStringBuilder: StringBuilder, private val myControl: EncodeControl) {

    /** Start of this literal's portion of buffer.  */
    private val myStartPos = myStringBuilder.length

    /** End of this literal's portion of buffer.  */
    private var myEndPos = myStartPos

    /** State of construction  */
    private var myState = INITIAL

    /**
     * Begin a new literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    /**
     * Begin a new literal that will be filled in incrementally.
     */
    @JvmOverloads
    constructor(control: EncodeControl = EncodeControl.forClient) : this(StringBuilder(1000), control) {
    }

    fun addParameter(param: String?, jsonLiteral: JSONLiteral?) {
        addParameter(param, jsonLiteral as Any?)
    }

    /**
     * Add an arbitrary parameter to an incomplete literal.  Note that any
     * parameter value whose string representation encodes to null will be
     * ignored (i.e., not added to the literal).
     *
     * @param param  The parameter name.
     * @param value  The parameter value.
     *
     * @throws Error if you try to add a parameter to literal that is already
     * complete.
     */
    fun addParameter(param: String?, value: Any?) {
        val start = myStringBuilder.length
        beginParameter(param)
        if (appendValueString(myStringBuilder, value, myControl)) {
            myStringBuilder.setLength(start)
        }
    }

    /**
     * Add an optional arbitrary parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional parameter value.
     */
    fun addParameterOpt(param: String?, value: Any?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add an array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (array) parameter value.
     */
    fun addParameter(param: String?, value: Array<Any?>) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    fun addParameter(param: String?, value: Array<String?>) {
        addParameter(param, value as Array<Any?>)
    }

    fun addParameter(param: String?, value: Array<JSONLiteral?>) {
        addParameter(param, value as Array<Any?>)
    }

    /**
     * Add a collection parameter to an incomplete literal (as an array).
     *
     * @param param  The parameter name.
     * @param value  The (collection) parameter value.
     */
    fun addParameter(param: String?, value: Collection<*>) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    /**
     * Add an optional array parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (array) parameter value.
     */
    fun addParameterOpt(param: String?, value: Array<Any?>?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add an optional collection parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null or the collection is empty, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (collection) parameter value.
     */
    fun addParameterOpt(param: String?, value: Collection<*>?) {
        if (value != null && value.isNotEmpty()) {
            addParameter(param, value)
        }
    }

    /**
     * Add an int array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (int array) value.
     */
    private fun addParameter(param: String, value: IntArray) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    /**
     * Add an optional int array parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the value
     * is null, the parameter is not added.
     *
     * @param param  The parameter name
     * @param value  The optional (int array) parameter value
     */
    fun addParameterOpt(param: String, value: IntArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a long array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (long array) value.
     */
    private fun addParameter(param: String, value: LongArray) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    /**
     * Add an optional long array parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (long array) parameter value.
     */
    fun addParameterOpt(param: String, value: LongArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a double array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (double array) value.
     */
    private fun addParameter(param: String, value: DoubleArray) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    /**
     * Add an optional double array parameter to an incomplete literal.  This
     * is similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (double array) parameter value.
     */
    fun addParameterOpt(param: String, value: DoubleArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a boolean array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (boolean array) value.
     */
    private fun addParameter(param: String, value: BooleanArray) {
        beginParameter(param)
        val arr = JSONLiteralArray(myStringBuilder, myControl)
        for (element in value) {
            arr.addElement(element)
        }
        arr.finish()
    }

    /**
     * Add an optional boolean array parameter to an incomplete literal.  This
     * is similar to [.addParameter], except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (boolean array) parameter value.
     */
    fun addParameterOpt(param: String, value: BooleanArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a [JsonArray] parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (JSONArray) value.
     */
    private fun addParameter(param: String, value: JsonArray) {
        addParameter(param, value as Any)
    }

    /**
     * Add an optional [JsonArray] parameter to an incomplete
     * literal.  This is similar to [.addParameter],
     * except that if the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (JSONArray) parameter value.
     */
    fun addParameterOpt(param: String, value: JsonArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add an array literal parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ([JSONLiteralArray]) parameter value.
     */
    fun addParameter(param: String?, value: JSONLiteralArray?) {
        addParameter(param, value as Any?)
    }

    /**
     * Add an optional array literal parameter to an incomplete literal.  This
     * is similar to [.addParameter], except
     * that if the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional ([JSONLiteralArray]) parameter value
     */
    fun addParameterOpt(param: String?, value: JSONLiteralArray?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add an object-valued parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ([Encodable]) parameter value.
     */
    fun addParameter(param: String?, value: Encodable?) {
        addParameter(param, value as Any?)
    }

    /**
     * Add an optional object-valued parameter to an incomplete literal.  This
     * is similar to [.addParameter], except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional ([Encodable]) parameter value.
     */
    fun addParameterOpt(param: String?, value: Encodable?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a floating point parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (double) parameter value.
     */
    fun addParameter(param: String?, value: Double) {
        addParameter(param, value as Any)
    }

    /**
     * Add a boolean parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (boolean) parameter value.
     */
    fun addParameter(param: String?, value: Boolean) {
        addParameter(param, value as Any)
    }

    /**
     * Add an integer parameter to an incomplete literal.
     *
     * @param param  The parameter name
     * @param value  The (int) parameter value.
     */
    fun addParameter(param: String?, value: Int) {
        addParameter(param, value as Any)
    }

    /**
     * Add a long parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (long) parameter value.
     */
    fun addParameter(param: String?, value: Long) {
        addParameter(param, value as Any)
    }

    /**
     * Add a JSON object parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ([JsonObject]) parameter value.
     */
    fun addParameter(param: String?, value: JsonObject?) {
        addParameter(param, value as Any?)
    }

    /**
     * Add an optional JSON object parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The ([JsonObject]) parameter value.
     */
    fun addParameterOpt(param: String?, value: JsonObject?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a reference parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ([Referenceable]) parameter value.
     */
    fun addParameter(param: String?, value: Referenceable) {
        addParameter(param, value.ref() as Any)
    }

    /**
     * Add an optional reference parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name
     * @param value  The parameter value
     */
    fun addParameterOpt(param: String?, value: Referenceable?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Add a string parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ([String]) parameter value.
     */
    fun addParameter(param: String?, value: String?) {
        addParameter(param, value as Any?)
    }

    /**
     * Add an optional string parameter to an incomplete literal.  This is
     * similar to [.addParameter], except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The parameter value.
     */
    fun addParameterOpt(param: String?, value: String?) {
        value?.let { addParameter(param, it) }
    }

    /**
     * Common base behavior for beginning encoding of any parameter.  Prepend
     * any necessary punctuation upon starting a new element, add the parameter
     * name, and update the state of construction accordingly.
     *
     * @param param  The parameter name.
     */
    private fun beginParameter(param: String?) {
        if (myState != COMPLETE) {
            if (myState == INITIAL) {
                /* Have added first parameter */
                // 1 = STARTED
                myState = 1
            } else {
                myStringBuilder.append(", ")
            }
            myStringBuilder.append('"')
            myStringBuilder.append(param)
            myStringBuilder.append("\":")
        } else {
            throw Error("attempt to add parameter to completed literal")
        }
    }

    /**
     * Obtain the encode control governing this literal.
     *
     * @return this literal's encode control.
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
            myStringBuilder.append('}')
            myState = COMPLETE
            myEndPos = myStringBuilder.length
        } else {
            throw Error("attempt to finish already completed literal")
        }
    }

    /**
     * Obtain the number of characters in the literal.
     *
     * @return the number of characters currently in this literal.
     */
    fun length(): Int {
        return myEndPos - myStartPos
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
     * Obtain a printable string representation of this literal, in whatever
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
        private const val INITIAL = 0 /* Have not yet added first parameter */
        private const val COMPLETE = 2 /* All done */

        /**
         * Convert a value of any type into the appropriate characters appended
         * onto the string under construction.
         *
         * @param buf  Buffer into which to encode the given value.
         * @param value  The value whose string encoding is sought
         * @param control  Encode control determining what flavor of encoding
         * is being done.
         *
         * @return true if the given value could not be encoded and so should be
         * ignored, false if everything worked fine.
         */
        @JvmStatic
        fun appendValueString(buf: StringBuilder, value: Any?, control: EncodeControl): Boolean {
            if (value == null) {
                /* Null is a special value all its own */
                buf.append("null")
            } else if (value is String) {
                val str = value
                buf.append('"')
                var start = 0
                for (i in 0 until str.length) {
                    val c = str[i]
                    var escape = '*'
                    when (c) {
                        '"' -> escape = '"'
                        '\\' -> escape = '\\'
                        '\b' -> escape = 'b'
                        '\u000C' -> escape = 'f'
                        '\n' -> escape = 'n'
                        '\r' -> escape = 'r'
                        '\t' -> escape = 't'
                    }
                    if (escape != '*') {
                        buf.append(str, start, i)
                        start = i + 1
                        buf.append('\\')
                        buf.append(escape)
                    }
                }
                buf.append(str.substring(start))
                buf.append('"')
            } else if (value is Number) {
                buf.append(value.toString())
            } else if (value is Encodable) {
                /* If the value knows how, ask it to encode itself */
                val encoded = value.encode(control)
                if (encoded != null) {
                    val result = encoded.sendableString()
                    buf.append(result)
                } else {
                    return true
                }
            } else if (value is JSONLiteral) {
                buf.append(value.myStringBuilder)
            } else if (value is JSONLiteralArray) {
                buf.append(value.stringBuilder())
            } else if (value is JsonObject) {
                JsonObjectSerialization.encodeLiteral(value, buf, control)
            } else if (value is JsonArray) {
                encodeLiteral(value, buf, control)
            } else {
                /* Else just convert the value to its natural string form */
                buf.append(value.toString())
            }
            return false
        }
    }

    init {
        myStringBuilder.append('{')
    }
}
