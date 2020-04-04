package org.elkoserver.server.presence

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.server.Server
import org.elkoserver.json.JSONObject
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.util.*
import java.util.function.Consumer

/**
 * Main state data structure in a Presence Server.
 */
internal class PresenceServer(
        /** Server object.  */
        private val myServer: Server,
        /** Trace object for diagnostics.  */
        private val tr: Trace,
        traceFactory: TraceFactory) {
    /** Database that this server stores stuff in.  */
    private val myODB: ObjDB?

    /** Table for mapping object references in messages.  */
    private val myRefTable: RefTable

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown: Boolean

    /** Set of currently connected actors.  */
    private val myActors: MutableSet<PresenceActor>

    /** Currently online users and what we know about them.  */
    private val myUsers: MutableMap<String, ActiveUser>

    /** Currently subscribing contexts whose users are visible, represented
     * as a map from context ref to associated client.  */
    private val myVisibles: MutableMap<String, PresenceActor>

    /** Known context metadata.  */
    private val myContextMetadata: MutableMap<String, JSONObject>

    /** The client object.  */
    private val myClientHandler: ClientHandler

    /** The social graphs theselves.  */
    private val mySocialGraphs: MutableMap<String, SocialGraph>

    /**
     * Get a read-only view of the set of connected actors.
     *
     * @return the set of connected actors.
     */
    fun actors(): Set<PresenceActor> {
        return Collections.unmodifiableSet(myActors)
    }

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
            tr.warningi("client " + client +
                    " attempts to subscribe to non-existent domain '" +
                    domain + "' in context " + context)
        } else {
            graph.domain().addSubscriber(context, client)
        }
    }

    fun updateDomain(domain: String, conf: JSONObject?, client: PresenceActor) {
        val graph = mySocialGraphs[domain]
        if (graph == null) {
            tr.warningi("client " + client +
                    " attempts to update non-existent domain '" +
                    domain + "'")
        } else {
            graph.update(this, graph.domain(), conf)
        }
    }

    fun removeSubscriber(context: String?) {
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
    fun noteUserMetadata(userRef: String, userMeta: JSONObject?) {
        val user = getUser(userRef)
        user.noteMetadata(userMeta)
    }

    /**
     * Take note of context metadata.
     *
     * @param contextRef  The context to which the metadata applies
     * @param contextMeta  The context metadata itself.
     */
    fun noteContextMetadata(contextRef: String, contextMeta: JSONObject) {
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
    fun getContextMetadata(contextRef: String): JSONObject? {
        return myContextMetadata[contextRef]
    }

    /**
     * Obtain the application trace object for this presence server.
     *
     * @return the prsence server's trace object.
     */
    fun appTrace(): Trace {
        return tr
    }

    /**
     * Get the handler for client messages.
     */
    fun clientHandler(): ClientHandler {
        return myClientHandler
    }

    /**
     * Obtain the active user info for a named user.
     *
     * @param userRef  The reference string for the user of interest
     *
     * @return the active user info for the named user, or null if that user
     * currently has no presences.
     */
    fun getActiveUser(userRef: String): ActiveUser? {
        return myUsers[userRef]
    }

    fun objDB(): ObjDB? {
        return myODB
    }

    /**
     * Return the object ref table.
     */
    fun refTable(): RefTable {
        return myRefTable
    }

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

    private fun isVisible(context: String): Boolean {
        return myVisibles.containsKey(context)
    }

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
                    tr.warningm("requested to remove user " + userRef +
                            " from unexpected presence " + context + "/" +
                            client)
                }
                if (user.presenceCount() == 0) {
                    myUsers.remove(userRef)
                }
            } else {
                tr.warningm("requested to remove unknown user " + userRef +
                        " from presence " + context + "/" + client)
            }
        }
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately without cleaning up.
     */
    fun shutdownServer(kill: Boolean) {
        for (graph in mySocialGraphs.values) {
            graph.shutdown()
        }
        myServer.shutdown(kill)
    }

    /**
     * Return the current number of active users
     */
    fun userCount(): Int {
        return myUsers.size
    }

    /**
     * Return an iterable collection of all the active users.
     */
    fun users(): Collection<ActiveUser> {
        return myUsers.values
    }

    init {
        myRefTable = RefTable(AlwaysBaseTypeResolver.theAlwaysBaseTypeResolver, traceFactory)
        myClientHandler = ClientHandler(this, traceFactory)
        myRefTable.addRef(myClientHandler)
        val myAdminHandler = AdminHandler(this, traceFactory)
        myRefTable.addRef(myAdminHandler)
        myActors = HashSet()
        myUsers = HashMap()
        myVisibles = HashMap()
        myContextMetadata = HashMap()
        myODB = myServer.openObjectDatabase("conf.presence")
        if (myODB == null) {
            tr.fatalError("no database specified")
        }
        myODB.addClass("graphtable", GraphTable::class.java)
        mySocialGraphs = HashMap()
        myODB.getObject("graphs", null, Consumer { obj: Any? ->
            if (obj != null) {
                val info = obj as GraphTable
                for (desc in info.graphs) {
                    val graph = desc.init(this@PresenceServer)
                    if (graph != null) {
                        mySocialGraphs[graph.domain().name()] = graph
                    }
                }
            } else {
                tr.warningi("unable to load social graph metadata table")
            }
        })
        isShuttingDown = false
        myServer.registerShutdownWatcher {
            isShuttingDown = true
            val actorListCopy: List<PresenceActor> = LinkedList(myActors)
            for (actor in actorListCopy) {
                actor.doDisconnect()
            }
            myODB.shutdown()
        }
    }
}