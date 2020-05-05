package org.elkoserver.json

object JSONLiteralFactory {
    /**
     * Begin a new literal representing a JSON message.
     *
     * @param target  The target to whom this message is addressed.
     * @param verb  The message verb.
     */
    fun targetVerb(target: Referenceable, verb: String) =
            JSONLiteral().apply {
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
    fun targetVerb(target: String, verb: String) =
            JSONLiteral().apply {
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
    fun type(type: String, control: EncodeControl) =
            JSONLiteral(control).apply {
                addParameter("type", type)
            }
}
