package org.elkoserver.json

object JsonArraySerialization {
    /**
     * Encode this JSONArray into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    @JvmStatic
    fun encodeLiteral(array: JsonArray, buf: StringBuilder, control: EncodeControl) {
        JSONLiteralArray(buf, control).apply {
            for (element in array) {
                addElement(element)
            }
            finish()
        }
    }

    /**
     * Convert this JSONArray into a JSONLiteralArray.
     *
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    fun literal(array: JsonArray, control: EncodeControl) =
            JSONLiteralArray(control).apply {
                for (element in array) {
                    addElement(element)
                }
                finish()
            }

    /**
     * Obtain a string representation of this array suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this array.
     */
    fun sendableString(array: JsonArray) = literal(array, EncodeControl.forClient).sendableString()
}