package org.elkoserver.server.presence

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory

/**
 * Generate a notification message to a client, telling it about the
 * status of a user's friends.
 *
 * @param user  The ref of the user whose friends are of interest
 * @param context  The context in which the user presence is being notified
 * @param friends  A list of the currently online members of the user's
 * social graph.
 */
internal fun msgGroupToUser(user: String, context: String?, friends: Map<Domain, List<ActiveUser.FriendInfo?>>) =
        JsonLiteralFactory.targetVerb("presence", "gtou").apply {
            addParameter("touser", user)
            addParameter("ctx", context)
            val group = JsonLiteralArray().apply {
                for ((key, value) in friends) {
                    val domainInfo = JsonLiteral().apply {
                        addParameter("domain", key.name)
                        addParameter("friends", value)
                        finish()
                    }
                    addElement(domainInfo)
                }
                finish()
            }
            addParameter("group", group)
            finish()
        }
