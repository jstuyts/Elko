package org.elkoserver.feature.deviceuser

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.UserMod

/**
 * This Mod holds device specific identity information for a user.
 *
 * @param uuid  The Device specific user ID, a device dependent user ID
 */
class DeviceUserMod @JsonMethod("uuid") constructor(private val uuid: String) : Mod(), UserMod {

    /**
     * Encode this mod for transmission or persistence.  This mod is
     * persisted but never transmitted to a client.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSONLiteral representing the encoded state of this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral? =
            if (control.toRepository()) {
                JsonLiteralFactory.type("deviceuser", control).apply {
                    addParameter("uuid", uuid)
                    finish()
                }
            } else {
                null
            }
}
