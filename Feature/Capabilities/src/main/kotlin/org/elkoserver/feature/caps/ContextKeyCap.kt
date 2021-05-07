package org.elkoserver.feature.caps

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ContextKey
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.UserMod

/**
 * Capability to enable entry to one or more entry controlled contexts.
 *
 * @param myContexts  Array of refs for the contexts to which this key grants
 *    entry permission.
 */
class ContextKeyCap @JsonMethod("contexts") constructor(
        raw: JsonObject,
        private val myContexts: Array<String>) : Cap(raw), ItemMod, UserMod, ContextKey {

    /**
     * Test if this capability enables entry to a particular context.
     *
     * @param contextRef  Reference string of the context of interest.
     *
     * @return true if this capability enables entry to the context designated
     * by 'contextRef', false if not.
     */
    override fun enablesEntry(contextRef: String?): Boolean {
        if (isExpired) {
            return false
        }
        return myContexts.contains(contextRef)
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("ctxkey", control).apply {
                encodeDefaultParameters(this)
                addParameter("contexts", myContexts)
                finish()
            }
}
