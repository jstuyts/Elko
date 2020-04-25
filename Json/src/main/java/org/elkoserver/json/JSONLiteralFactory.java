package org.elkoserver.json;

public class JSONLiteralFactory {
    /**
     * Begin a new literal representing a JSON message.
     *
     * @param target  The target to whom this message is addressed.
     * @param verb  The message verb.
     */
    public static JSONLiteral targetVerb(Referenceable target, String verb) {
        JSONLiteral result = new JSONLiteral();
        result.addParameter("to", target);
        result.addParameter("op", verb);
        return result;
    }

    /**
     * Begin a new literal representing a JSON message.
     *
     * @param target  The reference string of the target to whom this message
     *    is addressed.
     * @param verb  The message verb.
     */
    public static JSONLiteral targetVerb(String target, String verb) {
        JSONLiteral result = new JSONLiteral();
        result.addParameter("to", target);
        result.addParameter("op", verb);
        return result;
    }

    /**
     * Begin a new literal representing a JSON object.
     *
     * @param type  The type tag of this object.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public static JSONLiteral type(String type, EncodeControl control) {
        JSONLiteral result = new JSONLiteral(control);
        result.addParameter("type", type);
        return result;
    }
}
