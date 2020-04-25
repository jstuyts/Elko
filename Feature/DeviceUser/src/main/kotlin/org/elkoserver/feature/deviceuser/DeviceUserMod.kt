package org.elkoserver.feature.deviceuser

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.UserMod

/**
 * This Mod holds device specific identity information for a user.
 *
 * @param myDeviceUUID  The Device specific user ID, a device dependent user ID
 */
class DeviceUserMod @JSONMethod("uuid") constructor(private val myDeviceUUID: String) : Mod(), UserMod {

    /**
     * Get the Device UID
     */
    fun uuid(): String {
        return myDeviceUUID
    }

    /**
     * Encode this mod for transmission or persistence.  This mod is
     * persisted but never transmitted to a client.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSONLiteral representing the encoded state of this mod.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JSONLiteralFactory.type("deviceuser", control).apply {
                    addParameter("uuid", myDeviceUUID)
                    finish()
                }
            } else {
                null
            }
}
