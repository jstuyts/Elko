package org.elkoserver.json;

import org.elkoserver.json.JsonObject;

import java.util.Map;

public class JsonObjectSerialization {
    /**
     * Convert this JSONObject into a JSONLiteral.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    static JSONLiteral literal(JsonObject object, EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(control);

        /* What follows is a little bit of hackery to ensure that the canonical
           message and object properties ("to:", "op:", and "type:") are
           output first, regardless of what order the iterator spits all the
           properties out in.  This is strictly for the sake of legibility and
           has no deeper semantics. */

        literal.addParameterOpt("to", object.getString("to", null));
        literal.addParameterOpt("op", object.getString("op", null));
        literal.addParameterOpt("type", object.getString("type", null));

        for (Map.Entry<String, Object> entry : object.entrySet()) {
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
    static void encodeLiteral(JsonObject object, StringBuilder buf, EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(buf, control);

        for (Map.Entry<String, Object> entry : object.entrySet()) {
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
    public static String sendableString(JsonObject jsonObject) {
        return literal(jsonObject, EncodeControl.forClient).sendableString();
    }
}
