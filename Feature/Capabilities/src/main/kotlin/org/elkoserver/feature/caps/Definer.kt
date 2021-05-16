package org.elkoserver.feature.caps

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.ItemMod
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.UserMod

/**
 * Capability to enable external definition of persistent C-U-I objects.
 */
class Definer @JsonMethod constructor(raw: JsonObject) : Cap(raw), ItemMod, UserMod {
    /**
     * Message handler for a 'define' message.  This is a request to define a
     * new context, item, or user.
     *
     * <u>recv</u>: ` { to:*REF*, op:"define", into:*optREF*,
     * ref:*optREF*, obj:*OBJDESC* }`<br></br>
     * <u>send</u>: ` { to:*REF*, op:"define", ref:*REF* } `
     *
     * @param into  Container into which the new object should be placed
     * (optional, defaults to no container).
     * @param ref  Reference string for the new object (optional, defaults to
     * an automatically generated ref).
     * @param obj  JSON descriptor for the object itself
     *
     * @throws MessageHandlerException if 'from' is not the holder of this
     * definer capability, or if an explicit ID is given but an object of
     * that ID is loaded, or if a container is given and that container
     * object is loaded, or if the object descriptor is not a valid context,
     * item or user descriptor.
     */
    @JsonMethod("into", "ref", "obj")
    fun define(from: User, into: OptString, ref: OptString, obj: BasicObject) {
        ensureReachable(from)
        val intoRef = into.valueOrNull()
        if (intoRef != null) {
            if (context()[intoRef] != null) {
                throw MessageHandlerException("container $intoRef is loaded")
            }
        }
        val newRef = ref.valueOrNull()
        if (newRef != null) {
            if (context()[newRef] != null) {
                throw MessageHandlerException("proposed ref $newRef is loaded")
            }
        }
        val contextor = `object`().contextor()
        contextor.createObjectRecord(newRef, intoRef, obj)
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral {
        val result = JsonLiteralFactory.type("definer", control)
        encodeDefaultParameters(result)
        result.finish()
        return result
    }
}