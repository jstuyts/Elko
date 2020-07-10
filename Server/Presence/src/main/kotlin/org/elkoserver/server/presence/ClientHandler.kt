package org.elkoserver.server.presence

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the presence server 'client' protocol.
 *
 * The 'client' protocol consists of these messages:
 *
 * 'user' - Reports that a particular user has arrived or departed from a
 * context provided by the sender.
 *
 * @param myPresenceServer  The presence server object for this handler.
 */
internal class ClientHandler(private val myPresenceServer: PresenceServer, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'presence'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "presence"

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
        from.ensureAuthorizedClient()
        myPresenceServer.updateDomain(domain, conf, from)
    }

    /**
     * Handle the 'user' verb.
     *
     * Note the arrival or departure of an user.
     *
     * @param from  The client announcing the context change.
     * @param context  The context that the user entered or exited.
     * @param user  The user who entered or exited.
     * @param on  true on entry, false on exit.
     * @param userMeta  Optional user metadata.
     * @param contextMeta  Optional context metadata.
     */
    @JsonMethod("context", "user", "on", "?umeta", "?cmeta")
    fun user(from: PresenceActor, context: String, user: String,
             on: Boolean, userMeta: JsonObject?, contextMeta: JsonObject?) {
        from.ensureAuthorizedClient()
        val client = from.client!!
        if (userMeta != null) {
            client.noteUserMetadata(user, userMeta)
        }
        if (contextMeta != null) {
            client.noteContextMetadata(context, contextMeta)
        }
        if (on) {
            client.noteUserEntry(user, context)
        } else {
            client.noteUserExit(user, context)
        }
    }

    /**
     * Handle the 'subscribe' verb.
     *
     * Note a context's interest in presence updates pertaining to particular
     * domains, and begin sending them update traffic.
     *
     * @param context  The context who is interested
     * @param domains   The domains they are interested in
     * @param visible  Flag indicating if presence in the given context is
     * visible outside the context
     */
    @JsonMethod("context", "?domains", "visible")
    fun subscribe(from: PresenceActor, context: String, domains: Array<String>?, visible: OptBoolean) {
        if (domains != null) {
            for (domain in domains) {
                from.client!!.subscribeToUpdates(context, domain)
            }
        }
        if (visible.value(true)) {
            from.client!!.noteVisibleContext(context)
        }
    }

    /**
     * Handle the 'unsubscribe' verb.
     *
     * Note a context's cessation of interest in presence updates and stop
     * sending them update traffic.
     *
     * @param context  The context who is no longer interested
     */
    @JsonMethod("context")
    fun unsubscribe(from: PresenceActor, context: String) {
        from.client!!.unsubscribeToUpdates(context)
        from.client!!.noteInvisibleContext(context)
    }
}
