package org.elkoserver.json;

import java.util.ArrayList;

/**
 * A parsed JSON array.
 *
 * This class represents a JSON array that has been received or is being
 * constructed.
 */
public class JSONArray extends ArrayList<Object> {
    /**
     * Construct a new, empty array.
     */
    public JSONArray() {
        super();
    }

    /**
     * Encode this JSONArray into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    /* package */ void encodeLiteral(StringBuilder buf, EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(buf, control);
        for (Object element : this) {
            literal.addElement(element);
        }
        literal.finish();
    }

    /**
     * Convert this JSONArray into a JSONLiteralArray.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    private JSONLiteralArray literal(EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(control);
        for (Object element : this) {
            literal.addElement(element);
        }
        literal.finish();
        return literal;
    }

    /**
     * Obtain the JSON object value of an element.
     *
     * @param index  The index of the object value sought.
     *
     * @return  The JSON object value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a JSON object.
     */
    public JSONObject getObject(int index) throws JSONDecodingException {
        try {
            return (JSONObject) get(index);
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain a string representation of this array suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this array.
     */
    public String sendableString() {
        return literal(EncodeControl.forClient).sendableString();
    }

    /**
     * Obtain a printable string representation of this JSON array.
     *
     * @return a printable representation of this array.
     */
    public String toString() {
        return literal(EncodeControl.forRepository).sendableString();
    }
}
