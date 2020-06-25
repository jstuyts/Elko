package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.connectionretrier.ConnectionRetrierFactory
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Outbound group containing all the connected presence servers.
 *
 * @param server  Server object.
 * @param contextor  The server contextor.
 * @param presencers  List of HostDesc objects describing presence
 *    servers with whom to register.
 * @param tr  Trace object for diagnostics.
 */
internal class PresencerGroup(
        server: Server,
        contextor: Contextor,
        presencers: MutableList<HostDesc>,
        tr: Trace,
        gorgel: Gorgel,
        methodInvokerCommGorgel: Gorgel,
        timer: Timer,
        props: ElkoProperties,
        jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val presencerActorGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean,
        connectionRetrierFactory: ConnectionRetrierFactory)
    : OutboundGroup(
        "conf.presence",
        server,
        contextor,
        presencers,
        tr,
        gorgel,
        methodInvokerCommGorgel,
        timer,
        props,
        jsonToObjectDeserializer,
        connectionRetrierFactory) {
    /* ----- required OutboundGroup methods ----- */
    /**
     * Obtain the class of actors in this group (in this case, PresenceActor).
     *
     * @return this group's actor class.
     */
    override fun actorClass() = PresencerActor::class.java

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth, in this case, "presence".
     *
     * @return this group's label string.
     */
    override fun label() = "presence"

    /**
     * Get an actor object suitable to act on message traffic on a new
     * connection in this group.
     *
     * @param connection  The new connection
     * @param dispatcher   Message dispatcher for the message protocol on the
     * new connection
     * @param host  Descriptor information for the host the new connection is
     * connected to
     *
     * @return a new Actor object for use on this new connection
     */
    override fun provideActor(connection: Connection, dispatcher: MessageDispatcher, host: HostDesc): Actor {
        val presencer = PresencerActor(connection, dispatcher, this, host, presencerActorGorgel, mustSendDebugReplies)
        updatePresencer(presencer)
        return presencer
    }

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to, in this case,
     * "presence-client".
     *
     * @return a broker service string for this group.
     */
    override fun service() = "presence-client"

    /* ----- PresencerGroup methods ----- */
    /**
     * Tell the presence servers that a context has opened or closed, when
     * relevant.  In the case of an opening, this includes sending the presence
     * servers a list of the presence domains that the context is subscribing
     * to.
     *
     * @param context  The context
     * @param open  true if the context is being opened, false if being closed
     */
    fun noteContext(context: Context, open: Boolean) {
        val subscriptions = context.subscriptions
        if (subscriptions != null) {
            if (open) {
                send(msgSubscribe(context.ref(), subscriptions, true))
            } else {
                send(msgUnsubscribe(context.ref()))
            }
        }
    }

    /**
     * Tell the presence servers that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    fun noteUser(user: User, on: Boolean) {
        if (user.context().subscriptions != null) {
            send(msgUser(user.context().ref(), user.baseRef(), on,
                    userMeta(user), contextMeta(user.context())))
        }
    }

    /**
     * Update a newly connected presence server as to what users are present.
     *
     * @param presencer  The presence server to be updated.
     */
    private fun updatePresencer(presencer: PresencerActor) {
        for (user in contextor.users()) {
            presencer.send(msgUser(user.context().ref(), user.baseRef(), true,
                    userMeta(user),
                    contextMeta(user.context())))
        }
    }

    /**
     * Generate a metadata object for a context.  Right now, we only include
     * the name string.
     *
     * @param context  The context for which metadata is sought.
     *
     * @return JSON-encoded metadata for the give context.
     */
    private fun contextMeta(context: Context) =
            JSONLiteral().apply {
                addParameter("name", context.name)
                finish()
            }

    /**
     * Generate a metadata object for a user.  Right now, we only include the
     * name string.
     *
     * @param user  The user for whom metadata is sought.
     *
     * @return JSON-encoded metadata for the give user.
     */
    private fun userMeta(user: User) =
            JSONLiteral().apply {
                addParameter("name", user.name)
                finish()
            }

    companion object {
        /**
         * Create a "subscribe" message.
         *
         * @param context  The context that is subscribing
         * @param domains  The presence domains being subscribed to
         * @param visible  Flag indicating if presence in the given context is
         * visible outside the context
         */
        private fun msgSubscribe(context: String, domains: Array<String>, visible: Boolean) =
                JSONLiteralFactory.targetVerb("presence", "subscribe").apply {
                    addParameter("context", context)
                    addParameter("domains", domains)
                    addParameter("visible", visible)
                    finish()
                }

        /**
         * Create an "unsubscribe" message.
         *
         * @param context  The context that is ceasing to subscribe
         */
        private fun msgUnsubscribe(context: String) =
                JSONLiteralFactory.targetVerb("presence", "unsubscribe").apply {
                    addParameter("context", context)
                    finish()
                }

        /**
         * Create a "user" message.
         *
         * @param context  The context entered or exited.
         * @param user  Who entered or exited.
         * @param on  Flag indicating online or offline.
         */
        private fun msgUser(context: String, user: String, on: Boolean, userMeta: JSONLiteral, contextMeta: JSONLiteral) =
                JSONLiteralFactory.targetVerb("presence", "user").apply {
                    addParameter("context", context)
                    addParameter("user", user)
                    addParameter("on", on)
                    addParameterOpt("umeta", userMeta)
                    addParameterOpt("cmeta", contextMeta)
                    finish()
                }
    }

    init {
        connectHosts()
    }
}
