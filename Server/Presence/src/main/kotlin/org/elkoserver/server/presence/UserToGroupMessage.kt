package org.elkoserver.server.presence

import com.grack.nanojson.JsonObject
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory

/**
 * Generate a notification message to a client, telling it to inform a
 * group of users about the change in presence status of a user.
 *
 * @param user  The ref of the user whose presence changed
 * @param userMeta  Optional user metadata.
 * @param context  The context the user is or was in
 * @param on  true if the user came online, false if they went offline
 * @param friends  A collection of lists of the refs of the users who
 * should be informed, by domain and context
 * @param master  The presence server master instance.
 */
internal fun msgUserToGroup(user: String, userMeta: JsonObject?,
                            context: String?, on: Boolean,
                            friends: Map<Domain, Map<String, List<String?>>>,
                            master: PresenceServer) =
        JsonLiteralFactory.targetVerb("presence", "utog").apply {
            addParameter("user", user)
            addParameterOpt("umeta", userMeta)
            addParameter("ctx", context)
            addParameterOpt("cmeta", master.getContextMetadata(context!!))
            addParameter("on", on)
            val group = JsonLiteralArray().apply {
                for ((domain, who) in friends) {
                    val obj = JsonLiteral().apply {
                        addParameter("domain", domain.name)
                        val whoArr = JsonLiteralArray().apply {
                            for ((key, value) in who) {
                                val ctxInfo = JsonLiteral().apply {
                                    addParameter("ctx", key)
                                    addParameter("users", value)
                                    finish()
                                }
                                addElement(ctxInfo)
                            }
                            finish()
                        }
                        addParameter("who", whoArr)
                        finish()
                    }
                    addElement(obj)
                }
                finish()
            }
            addParameter("togroup", group)
            finish()
        }
