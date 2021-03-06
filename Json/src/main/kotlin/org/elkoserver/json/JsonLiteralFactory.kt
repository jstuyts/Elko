package org.elkoserver.json

object JsonLiteralFactory {
    /**
     * Begin a new literal representing a JSON message.
     *
     * @param target  The target to whom this message is addressed.
     * @param verb  The message verb.
     */
    fun targetVerb(target: Referenceable, verb: String): JsonLiteral =
            JsonLiteral().apply {
                addParameter("to", target)
                addParameter("op", verb)
            }

    /**
     * Begin a new literal representing a JSON message.
     *
     * @param target  The reference string of the target to whom this message
     * is addressed.
     * @param verb  The message verb.
     */
    fun targetVerb(target: String, verb: String): JsonLiteral =
            JsonLiteral().apply {
                addParameter("to", target)
                addParameter("op", verb)
            }

    /**
     * Begin a new literal representing a JSON object.
     *
     * @param type  The type tag of this object.
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    fun type(type: String, control: EncodeControl): JsonLiteral =
            JsonLiteral(control).apply {
                addParameter("type", type)
            }
}
