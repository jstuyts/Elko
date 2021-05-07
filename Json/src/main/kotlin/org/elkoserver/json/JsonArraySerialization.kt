package org.elkoserver.json

import com.grack.nanojson.JsonArray
import org.elkoserver.json.EncodeControl.ForClientEncodeControl

object JsonArraySerialization {
    /**
     * Encode this JSONArray into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    fun encodeLiteral(array: JsonArray, buf: StringBuilder, control: EncodeControl) {
        JsonLiteralArray(buf, control).apply {
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
    fun literal(array: JsonArray, control: EncodeControl): JsonLiteralArray =
            JsonLiteralArray(control).apply {
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
    fun sendableString(array: JsonArray): String = literal(array, ForClientEncodeControl).sendableString()
}