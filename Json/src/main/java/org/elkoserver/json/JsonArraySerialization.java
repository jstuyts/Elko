package org.elkoserver.json;

public class JsonArraySerialization {
    /**
     * Encode this JSONArray into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    static void encodeLiteral(JSONArray array, StringBuilder buf, EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(buf, control);
        for (Object element : array) {
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
    static JSONLiteralArray literal(JSONArray array, EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(control);
        for (Object element : array) {
            literal.addElement(element);
        }
        literal.finish();
        return literal;
    }

    /**
     * Obtain a string representation of this array suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this array.
     */
    public static String sendableString(JSONArray array) {
        return literal(array, EncodeControl.forClient).sendableString();
    }
}
