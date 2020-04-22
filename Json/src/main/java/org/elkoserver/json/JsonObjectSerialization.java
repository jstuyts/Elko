package org.elkoserver.json;

import java.util.Map;

public class JsonObjectSerialization {
    /**
     * Convert this JSONObject into a JSONLiteral.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    static JSONLiteral literal(JSONObject object, EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(control);

        /* What follows is a little bit of hackery to ensure that the canonical
           message and object properties ("to:", "op:", and "type:") are
           output first, regardless of what order the iterator spits all the
           properties out in.  This is strictly for the sake of legibility and
           has no deeper semantics. */

        literal.addParameterOpt("to", object.target());
        literal.addParameterOpt("op", object.verb());
        literal.addParameterOpt("type", object.type());

        for (Map.Entry<String, Object> entry : object.properties()) {
            String key = entry.getKey();
            if (!key.equals("to") && !key.equals("op") && !key.equals("type")){
                literal.addParameter(key, entry.getValue());
            }
        }
        literal.finish();
        return literal;
    }

    /**
     * Encode this JSONObject into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    static void encodeLiteral(JSONObject object, StringBuilder buf, EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(buf, control);

        for (Map.Entry<String, Object> entry : object.properties()) {
            literal.addParameter(entry.getKey(), entry.getValue());
        }
        literal.finish();
    }

    /**
     * Obtain a {@link String} representation of this object suitable for
     * output to a connection.
     *
     * @return a sendable {@link String} representation of this object
     */
    public static String sendableString(JSONObject jsonObject) {
        return literal(jsonObject, EncodeControl.forClient).sendableString();
    }
}
