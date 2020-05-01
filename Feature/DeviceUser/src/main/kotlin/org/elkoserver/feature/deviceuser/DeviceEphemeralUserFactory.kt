package org.elkoserver.feature.deviceuser

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JsonObject
import org.elkoserver.server.context.Contextor
import org.elkoserver.server.context.User
import java.util.function.Consumer

/**
 * Factory that generates a non-persistent user object from connected mobile
 * device information.
 *
 * @param device  The name of the device (IOS, etc).
 */
class DeviceEphemeralUserFactory @JSONMethod("device") constructor(device: String) : DevicePersistentUserFactory(device) {
    /**
     * Synthesize an ephemeral user object based on user description info
     * fetched from the Device.
     *
     * @param contextor  The contextor of the server in which the synthetic
     * user will be present
     * @param connection  The connection over which the new user presented
     * themselves.
     * @param param  Arbitrary JSON object parameterizing the construction.
     * @param handler  Handler to invoke with the resulting user object, or
     * with null if the user object could not be produced.
     */
    override fun provideUser(contextor: Contextor, connection: Connection?, param: JsonObject?, handler: Consumer<Any?>) {
        val user = (extractCredentials(contextor.appTrace(), param)?.let { creds ->
            User(creds.name, null, null, null).apply {
                markAsEphemeral()
                objectIsComplete()
            }
        })
        handler.accept(user)
    }
}
