package org.elkoserver.server.presence

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the presence server 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'reinit' - Requests the presence server to reinitialize itself.
 *
 * 'shutdown' - Requests the presence server to shut down, with an option to
 * force abrupt termination.
 *
 * 'dump' - Request a dump of some or all of the presence server's state.
 *
 * @param myPresenceServer  The presence server administered by this handler
 */
internal class AdminHandler(private val myPresenceServer: PresenceServer, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "admin"

    /**
     * Handle the 'dump' verb.
     *
     * Request a dump of the presence server's state.
     *
     * @param from  The administrator asking for the information.
     * @param depth  Depth limit for the dump: 0 ==> counts only, 1 ==> adds
     * user names, 2 ==> adds presence info, 3 ==> adds social graph data
     * @param optUser  A user to limit the dump to
     */
    @JsonMethod("depth", "user")
    fun dump(from: PresenceActor, depth: Int, optUser: OptString) {
        from.ensureAuthorizedAdmin()
        val userName = optUser.value<String?>(null)
        var numUsers = 0
        var numPresences = 0
        val userDump = JsonLiteralArray()
        for (user in myPresenceServer.users()) {
            if (userName == null || user.ref == userName) {
                ++numUsers
                numPresences += user.presenceCount()
                if (depth > 0) {
                    val elem = JsonLiteral().apply {
                        addParameter("user", user.ref)
                        if (depth > 1) {
                            addParameter("pres", user.presences)
                        }
                        if (depth > 2) {
                            addParameter("conn", user.encodeFriendsDump())
                        }
                        finish()
                    }
                    userDump.addElement(elem)
                }
            }
        }
        userDump.finish()
        from.send(msgDump(this, numUsers, numPresences,
                if (depth > 0) userDump else null))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the presence server be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JsonMethod
    fun reinit(from: PresenceActor) {
        from.ensureAuthorizedAdmin()
        myPresenceServer.reinitServer()
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the presence server be shut down.
     *
     * @param from  The administrator sending the message.
     */
    @JsonMethod
    fun shutdown(from: PresenceActor) {
        from.ensureAuthorizedAdmin()
        myPresenceServer.shutdownServer()
    }

    /**
     * Handle the 'update' verb.
     *
     * Update the state of a domain's social graph object.
     *
     * @param from  The client issuing the update.
     * @param domain  The domain being updated.
     * @param conf  Domain-specific configuration update parameters.
     */
    @JsonMethod("domain", "conf")
    fun update(from: PresenceActor, domain: String, conf: JsonObject) {
        from.ensureAuthorizedAdmin()
        myPresenceServer.updateDomain(domain, conf, from)
    }
}
