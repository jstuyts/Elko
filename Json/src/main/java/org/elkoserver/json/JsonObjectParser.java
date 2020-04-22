package org.elkoserver.json;

public class JsonObjectParser {
    /**
     * Create a JSON object by parsing a JSON object literal string.  If this
     * string contains more than one JSON literal, only the first one will be
     * parsed; if you have a string containing more than one JSON literal, use
     * {@link Parser} instead.
     *
     * @param str  A JSON literal string that will be parsed and turned into
     *    the corresponding JSONObject.
     */
    public static JSONObject parse(String str) throws SyntaxError {
        JSONObject result = new Parser(str).parseObjectLiteral();
        if (result == null) {
            throw new SyntaxError("empty JSON string");
        }
        return result;
    }
}
