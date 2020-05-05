package org.elkoserver.feature.deviceuser

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JSONDecodingException
import org.elkoserver.json.JsonObject
import org.elkoserver.server.context.Contextor
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserFactory
import org.elkoserver.util.trace.Trace
import java.util.concurrent.Callable
import java.util.function.Consumer

/**
 * Factory that generates a persistent user object from connected mobile device
 * information.
 *
 * @param myDevice  The name of the device (IOS, etc).
 */
open class DevicePersistentUserFactory @JSONMethod("device") internal constructor(private val myDevice: String) : UserFactory {
    /**
     * Produce a user object.
     *
     * @param contextor The contextor of the server in which the requested
     * user will be present
     * @param connection  The connection over which the new user presented
     * themselves.
     * @param param   Arbitrary JSON object parameterizing the construction.
     * this is analogous to the user record read from the ODB, but may be
     * anything that makes sense for the particular factory implementation.
     * Of course, the sender of this parameter must be coordinated with the
     * factory implementation.
     * @param handler   Handler to be called with the result.  The result will
     * be the user object that was produced, or null if none could be.
     */
    override fun provideUser(contextor: Contextor, connection: Connection?, param: JsonObject?, handler: Consumer<in User?>) {
        val creds = extractCredentials(contextor.appTrace(), param)
        if (creds == null) {
            handler.accept(null)
        } else {
            contextor.server().enqueueSlowTask(Callable {
                contextor.queryObjects(deviceQuery(creds.uuid), null, 0,
                        DeviceQueryResultHandler(contextor, creds, handler))
                null
            }, null)
        }
    }

    private class DeviceQueryResultHandler internal constructor(private val myContextor: Contextor, private val myCreds: DeviceCredentials,
                                                                private val myHandler: Consumer<in User?>) : Consumer<Any?> {
        override fun accept(queryResult: Any?) {
            val user: User
            @Suppress("UNCHECKED_CAST") val result = queryResult as Array<Any>?
            if (result != null && result.isNotEmpty()) {
                if (result.size > 1) {
                    myContextor.appTrace().warningm("uuid query loaded ${result.size} users, choosing first")
                }
                user = result[0] as User
            } else {
                var name = myCreds.name
                if (name == null) {
                    name = "AnonUser"
                }
                val uuid = myCreds.uuid
                myContextor.appTrace().eventi("synthesizing user record for $uuid")
                val mod = DeviceUserMod(uuid)
                user = User(name, arrayOf<Mod>(mod), null, myContextor.uniqueID("u"))
                user.markAsChanged()
            }
            myHandler.accept(user)
        }

    }

    private fun deviceQuery(uuid: String): JsonObject {
        // { type: "user",
        //   mods: { $elemMatch: { type: "deviceuser", uuid: UUID }}}
        val modMatchPattern = JsonObject().apply {
            put("type", "deviceuser")
            put("uuid", uuid)
        }
        val modMatch = JsonObject().apply {
            put("\$elemMatch", modMatchPattern)
        }
        return JsonObject().apply {
            put("type", "user")
            put("mods", modMatch)
        }
    }

    /**
     * Extract the user login credentials from a user factory parameter object.
     *
     * @param appTrace  Trace object for error logging
     * @param param  User factory parameters
     *
     * @return a credentials object as described by the parameter object given,
     * or null if parameters were missing or invalid somehow.
     */
    fun extractCredentials(appTrace: Trace, param: JsonObject?): DeviceCredentials? {
        checkNotNull(param)

        try {
            val uuid = param.getString("uuid")
            if (uuid == null) {
                appTrace.errorm("bad parameter: missing uuid")
                return null
            }
            var name = param.getString("name")
            if (name == null) {
                name = param.getString("nickname")
            }
            return DeviceCredentials(uuid, name)
        } catch (e: JSONDecodingException) {
            appTrace.errorm("bad parameter: $e")
        }
        return null
    }

    /**
     * Struct object holding login info for a device user.
     */
    class DeviceCredentials internal constructor(
            /** The device ID  */
            val uuid: String,
            /** Name of the user  */
            val name: String?)

}
