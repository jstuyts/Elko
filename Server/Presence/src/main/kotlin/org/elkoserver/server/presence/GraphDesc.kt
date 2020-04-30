package org.elkoserver.server.presence

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.JsonObject
import java.lang.reflect.InvocationTargetException

internal class GraphDesc @JSONMethod("class", "name", "?conf") constructor(private val myClassName: String, private val myGraphName: String, conf: JsonObject?) {
    private val myConf: JsonObject = conf ?: JsonObject()

    fun init(master: PresenceServer) =
            try {
                (Class.forName(myClassName).getConstructor().newInstance() as SocialGraph).apply {
                    init(master, Domain(myGraphName), myConf)
                }
            } catch (e: ClassNotFoundException) {
                master.appTrace().errori("class $myClassName not found")
                null
            } catch (e: InstantiationException) {
                master.appTrace().errorm("unable to instantiate $myClassName: $e")
                null
            } catch (e: IllegalAccessException) {
                master.appTrace().errorm("unable to instantiate $myClassName: $e")
                null
            } catch (e: NoSuchMethodException) {
                master.appTrace().errorm("class $myClassName does not have a public no-arg constructor: $e")
                null
            } catch (e: InvocationTargetException) {
                master.appTrace().errorm("error occurred during instantiation of $myClassName: ${e.cause}")
                null
            }
}
