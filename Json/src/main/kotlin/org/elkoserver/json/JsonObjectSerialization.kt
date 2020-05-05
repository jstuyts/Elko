package org.elkoserver.json

object JsonObjectSerialization {
    /**
     * Convert this JSONObject into a JSONLiteral.
     *
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    fun literal(`object`: JsonObject, control: EncodeControl) =
            JSONLiteral(control).apply {
                /* What follows is a little bit of hackery to ensure that the canonical
                   message and object properties ("to:", "op:", and "type:") are
                   output first, regardless of what order the iterator spits all the
                   properties out in.  This is strictly for the sake of legibility and
                   has no deeper semantics. */
                addParameterOpt("to", `object`.getString("to", null))
                addParameterOpt("op", `object`.getString("op", null))
                addParameterOpt("type", `object`.getString("type", null))
                for ((key, value) in `object`.entrySet()) {
                    if (key != "to" && key != "op" && key != "type") {
                        addParameter(key, value)
                    }
                }
                finish()
            }

    /**
     * Encode this JSONObject into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     * is being done.
     */
    fun encodeLiteral(`object`: JsonObject, buf: StringBuilder, control: EncodeControl) {
        JSONLiteral(buf, control).apply {
            for ((key, value) in `object`.entrySet()) {
                addParameter(key, value)
            }
            finish()
        }
    }

    /**
     * Obtain a [String] representation of this object suitable for
     * output to a connection.
     *
     * @return a sendable [String] representation of this object
     */
    @JvmStatic
    fun sendableString(jsonObject: JsonObject) = literal(jsonObject, EncodeControl.forClient).sendableString()
}
