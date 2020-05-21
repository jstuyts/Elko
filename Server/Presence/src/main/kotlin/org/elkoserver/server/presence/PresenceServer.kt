package org.elkoserver.server.presence

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.json.JsonObject
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Main state data structure in a Presence Server.
 */
internal class PresenceServer(
        private val myServer: Server,
        private val gorgel: Gorgel,
        private val graphDescGorgel: Gorgel,
        private val socialGraphGorgel: Gorgel,
        traceFactory: TraceFactory,
        clock: Clock) {
    /** Database that this server stores stuff in.  */
    internal val objDB: ObjDB

    /** Table for mapping object references in messages.  */
    internal val refTable = RefTable(AlwaysBaseTypeResolver, traceFactory, clock)

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown = false

    /** Set of currently connected actors.  */
    private val myActors: MutableSet<PresenceActor>

    /** Currently online users and what we know about them.  */
    private val myUsers: MutableMap<String, ActiveUser>

    /** Currently subscribing contexts whose users are visible, represented
     * as a map from context ref to associated client.  */
    private val myVisibles: MutableMap<String, PresenceActor>

    /** Known context metadata.  */
    private val myContextMetadata: MutableMap<String, JsonObject>

    /** The client object.  */
    private val clientHandler = ClientHandler(this, traceFactory)

    /** The social graphs themselves.  */
    private val mySocialGraphs = HashMap<String, SocialGraph>()

    /**
     * Get a read-only view of the set of connected actors.
     *
     * @return the set of connected actors.
     */
    fun actors(): Set<PresenceActor> = myActors

    /**
     * Add a new actor to the table of connected actors.
     *
     * @param actor  The actor to add.
     */
    fun addActor(actor: PresenceActor) {
        myActors.add(actor)
    }

    fun addSubscriber(context: String, domain: String, client: PresenceActor) {
        val graph = mySocialGraphs[domain]
        if (graph == null) {
            gorgel.warn("client $client attempts to subscribe to non-existent domain '$domain' in context $context")
        } else {
            graph.domain().addSubscriber(context, client)
        }
    }

    fun updateDomain(domain: String, conf: JsonObject, client: PresenceActor) {
        val graph = mySocialGraphs[domain]
        if (graph == null) {
            gorgel.warn("client $client attempts to update non-existent domain '$domain'")
        } else {
            graph.update(this, graph.domain(), conf)
        }
    }

    fun removeSubscriber(context: String) {
        for (graph in mySocialGraphs.values) {
            graph.domain().removeSubscriber(context)
        }
    }

    fun addVisibleContext(context: String, client: PresenceActor) {
        myVisibles[context] = client
    }

    fun removeVisibleContext(context: String) {
        myVisibles.remove(context)
    }

    private fun getUser(userRef: String): ActiveUser {
        var user = myUsers[userRef]
        if (user == null) {
            user = ActiveUser(userRef)
            myUsers[userRef] = user
            for (graph in mySocialGraphs.values) {
                graph.loadUserGraph(user)
            }
        }
        return user
    }

    /**
     * Add a new user to the collection of online user presences.
     * @param userRef  The reference string for the new user.
     * @param context  The name of the context the user is in.
     */
    fun addUserPresence(userRef: String, context: String) {
        if (isVisible(context)) {
            val user = getUser(userRef)
            user.addPresence(context, this)
        }
    }

    /**
     * Take note of user metadata.
     *
     * @param userRef  The user to whom the metadata applies
     * @param userMeta  The user metadata itself.
     */
    fun noteUserMetadata(userRef: String, userMeta: JsonObject?) {
        val user = getUser(userRef)
        user.noteMetadata(userMeta)
    }

    /**
     * Take note of context metadata.
     *
     * @param contextRef  The context to which the metadata applies
     * @param contextMeta  The context metadata itself.
     */
    fun noteContextMetadata(contextRef: String, contextMeta: JsonObject) {
        myContextMetadata[contextRef] = contextMeta
    }

    /**
     * Obtain whatever metadata this presence server is holding for a given
     * context.
     *
     * @param contextRef  The ref of the context of interest.
     *
     * @return a metadata object for the given context, or null if there is
     * none.
     */
    fun getContextMetadata(contextRef: String): JsonObject? = myContextMetadata[contextRef]

    /**
     * Obtain the active user info for a named user.
     *
     * @param userRef  The reference string for the user of interest
     *
     * @return the active user info for the named user, or null if that user
     * currently has no presences.
     */
    fun getActiveUser(userRef: String): ActiveUser? = myUsers[userRef]

    /**
     * Reinitialize the server.
     */
    fun reinitServer() {
        myServer.reinit()
    }

    /**
     * Remove an actor from the set of connected actors.
     *
     * @param actor  The actor to remove.
     */
    fun removeActor(actor: PresenceActor) {
        myActors.remove(actor)
        for (graph in mySocialGraphs.values) {
            graph.domain().removeClient(actor)
        }
        myVisibles.values.removeIf { client: PresenceActor -> client === actor }
    }

    private fun isVisible(context: String): Boolean = myVisibles.containsKey(context)

    /**
     * Remove a departing user from the collection of online user presences.
     *
     * @param userRef  The reference string for the departing user.
     * @param context  The name of the context the user is leaving.
     * @param client  Actor connected to context server user was on.
     */
    fun removeUserPresence(userRef: String, context: String,
                           client: PresenceActor) {
        if (isVisible(context)) {
            val user = myUsers[userRef]
            if (user != null) {
                if (!user.removePresence(context, this)) {
                    gorgel.warn("requested to remove user $userRef from unexpected presence $context/$client")
                }
                if (user.presenceCount() == 0) {
                    myUsers.remove(userRef)
                }
            } else {
                gorgel.warn("requested to remove unknown user $userRef from presence $context/$client")
            }
        }
    }

    /**
     * Shutdown the server.
     */
    fun shutdownServer() {
        for (graph in mySocialGraphs.values) {
            graph.shutdown()
        }
        myServer.shutdown()
    }

    /**
     * Return the current number of active users
     */
    fun userCount(): Int = myUsers.size

    /**
     * Return an iterable collection of all the active users.
     */
    fun users(): Collection<ActiveUser> = myUsers.values

    init {
        refTable.addRef(clientHandler)
        val myAdminHandler = AdminHandler(this, traceFactory)
        refTable.addRef(myAdminHandler)
        myActors = HashSet()
        myUsers = HashMap()
        myVisibles = HashMap()
        myContextMetadata = HashMap()
        objDB = myServer.openObjectDatabase("conf.presence") ?: throw IllegalStateException("no database specified")
        objDB.addClass("graphtable", GraphTable::class.java)
        objDB.getObject("graphs", null, Consumer { obj: Any? ->
            if (obj != null) {
                val info = obj as GraphTable
                info.graphs
                        .mapNotNull { it.init(this@PresenceServer, graphDescGorgel, socialGraphGorgel) }
                        .forEach { mySocialGraphs[it.domain().name] = it }
            } else {
                gorgel.warn("unable to load social graph metadata table")
            }
        })
        myServer.registerShutdownWatcher(object : ShutdownWatcher {
            override fun noteShutdown() {
                isShuttingDown = true
                val actorListCopy: List<PresenceActor> = LinkedList(myActors)
                for (actor in actorListCopy) {
                    actor.doDisconnect()
                }
                objDB.shutdown()
            }
        })
    }
}
