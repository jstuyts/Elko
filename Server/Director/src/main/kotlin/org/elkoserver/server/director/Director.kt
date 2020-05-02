package org.elkoserver.server.director

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.Server
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.util.HashMapMulti
import org.elkoserver.util.HashSetMulti
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock
import java.util.Collections
import java.util.LinkedList
import java.util.TreeMap

/**
 * Main state data structure in a Director.
 *
 * @param myServer  Server object.
 * @param tr  Trace object for diagnostics.
 */
internal class Director(private val myServer: Server, private val tr: Trace, traceFactory: TraceFactory, clock: Clock?) {
    /** Table for mapping object references in messages.  */
    private val myRefTable = RefTable(AlwaysBaseTypeResolver, traceFactory, clock)

    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown = false

    /** Open contexts.  Maps context names to OpenContext objects  */
    private val myContexts: MutableMap<String, OpenContext> = HashMap()

    /** Open context clone groups.  Maps context set names to sets of
     * OpenContext objects.  */
    private val myContextCloneSets = HashMapMulti<String, OpenContext>()

    /** Online users.  Maps user names to sets of OpenContext objects.  */
    private val myUsers = HashMapMulti<String, OpenContext>()

    /** Online user context clone groups.  Maps user set names to sets of
     * OpenContext objects.  */
    private val myUserCloneSets = HashMapMulti<String, OpenContext>()

    /** Currently active providers (sorted by load).  */
    private val myProviders = TreeMap<Provider, Provider>()

    /** Estimated amount of load increase from sending a user to a context.  */
    private val myEstimatedLoadIncrement: Double

    /** Maximum number of providers supported.  */
    private val myProviderLimit: Int

    /** The admin object.  */
    private val myAdminHandler: AdminHandler

    /** The provider object.  */
    private val myProviderHandler = ProviderHandler(this, traceFactory)

    /** Map of context names to sets of watching admin actors.  */
    private val myWatchedContexts = HashMapMulti<String, DirectorActor>()

    /** Map of user names to sets of watching admin actors.  */
    private val myWatchedUsers = HashMapMulti<String, DirectorActor>()

    /**
     * Add a new context to the table of known contexts.
     *
     * @param context  Context description.
     */
    fun addContext(context: OpenContext) {
        var name = context.name()
        myContexts[name] = context
        noteWatchedContext(name)
        if (context.isClone) {
            name = context.cloneSetName()!!
            myContextCloneSets.add(name, context)
            noteWatchedContext(name)
        }
    }

    /**
     * Add a new provider to the set of known providers.
     *
     * @param provider  The provider to add.
     */
    fun addProvider(provider: Provider) {
        myProviders[provider] = provider
    }

    /**
     * Add a new user to the table of known users.
     *
     * @param userName  The name of the user.
     * @param context  The context the user is in.
     */
    fun addUser(userName: String, context: OpenContext) {
        myUsers.add(userName, context)
        noteWatchedUser(userName)
        if (isUserClone(userName)) {
            val clonedUserName = userCloneSetName(userName)
            myUserCloneSets.add(clonedUserName, context)
            noteWatchedUser(clonedUserName)
        }
    }

    /**
     * Return a set of context clones.
     *
     * @param contextName  The name of the clone context group sought
     *
     * @return a multi-set of the contexts that are clones of 'contextName'.
     */
    fun contextClones(contextName: String) = myContextCloneSets.getMulti(contextName)

    /**
     * Return a read-only view of the set of known contexts.
     *
     * @return the collection of known contexts.
     */
    fun contexts() = Collections.unmodifiableCollection(myContexts.values)

    /**
     * Do the work of relaying a message embedded in another message.
     *
     * @param from  The entity being relayed from.
     * @param optContext  The context to be broadcast to.
     * @param optUser  The user to be broadcast to.
     * @param msg  The message to relay.
     */
    fun doRelay(from: DirectorActor?, optContext: OptString, optUser: OptString, msg: JsonObject) {
        val contextName = optContext.value<String?>(null)
        val userName = optUser.value<String?>(null)
        val relay = msgRelay(myProviderHandler, contextName, userName, msg)
        targetedBroadCast(from?.provider(), contextName, userName, relay)
    }

    /**
     * Lookup a context by name.
     *
     * @param contextName  The name of the context sought.
     *
     * @return the context with the given name, or null if there isn't one.
     */
    fun getContext(contextName: String) = myContexts[contextName]

    /**
     * Test if a particular user is online.
     */
    private fun hasUser(userName: String) =
            myUsers.containsKey(userName) || myUserCloneSets.containsKey(userName)

    /**
     * Test if this director already has all the providers it can handle.
     *
     * @return true if the number of providers connected reaches or exceeds
     * the provider limit.
     */
    val isFull: Boolean
        get() = myProviderLimit > 0 && myProviders.size >= myProviderLimit

    /**
     * Determine what provider to use for some context service.  Pick the least
     * loaded appropriate provider.
     *
     * @param contextName  The name of the context sought.
     * @param protocol  The protocol desired for speaking to the provider.
     * @param internal  Flag indicating a request from within the server farm
     *
     * @return the appropriate server for the given service and protocol, or
     * null if there is no appropriate server.
     */
    fun locateProvider(contextName: String, protocol: String?,
                       internal: Boolean): Provider? {
        val service = serviceName(contextName)
        val logString = StringBuilder()
        for (provider in myProviders.keys) {
            logString.append("[").append(provider).append("/").append(provider.loadFactor()).append("]")
        }
        for (provider in myProviders.keys) {
            if (provider.willServe(service, protocol!!, internal)) {
                provider.setLoadFactor(provider.loadFactor() + myEstimatedLoadIncrement)
                tr.eventm("choose $provider from $logString")
                return provider
            }
        }
        return null
    }

    /**
     * Check if anybody needs to be notified about a watched context.
     *
     * @param contextName  The name of the context that opened or closed.
     */
    private fun noteWatchedContext(contextName: String) {
        for (admin in myWatchedContexts.getMulti(contextName)) {
            myAdminHandler.findContext(contextName, admin)
        }
    }

    /**
     * Check if anybody needs to be notified about a watched user.
     *
     * @param userName  The name of the user who entered or exited.
     */
    private fun noteWatchedUser(userName: String) {
        for (admin in myWatchedUsers.getMulti(userName)) {
            myAdminHandler.findUser(userName, admin)
        }
    }

    /**
     * Return this director's provider handler object.
     */
    fun providerHandler() = myProviderHandler

    /**
     * Get a read-only view of the set of known providers.
     *
     * @return the set of known providers.
     */
    fun providers() = Collections.unmodifiableSet(myProviders.keys)

    /**
     * Return the object ref table.
     */
    fun refTable() = myRefTable

    /**
     * Reinitialize the server.
     */
    fun reinitServer() {
        myServer.reinit()
    }

    /**
     * Remove a context from the set of known contexts.
     *
     * @param context  The context to remove.
     */
    fun removeContext(context: OpenContext) {
        for (userName in context.users()) {
            removeUser(userName, context)
        }
        var name = context.name()
        myContexts.remove(name)
        noteWatchedContext(name)
        if (context.isClone) {
            name = context.cloneSetName()!!
            myContextCloneSets.remove(name, context)
            noteWatchedContext(name)
        }
    }

    /**
     * Remove a provider from the set of known providers.
     *
     * @param provider  The provider to remove.
     */
    fun removeProvider(provider: Provider) {
        myProviders.remove(provider)
    }

    /**
     * Remove a user from some context in the set of known users.
     *
     * @param userName  The name of the user.
     * @param context  The context the user was in.
     */
    fun removeUser(userName: String, context: OpenContext) {
        myUsers.remove(userName, context)
        noteWatchedUser(userName)
        if (isUserClone(userName)) {
            val clonedUserName = userCloneSetName(userName)
            myUserCloneSets.remove(clonedUserName, context)
            noteWatchedUser(clonedUserName)
        }
    }

    /**
     * Extract the service name from a context name.
     *
     * @param context  The context name.
     */
    private fun serviceName(context: String): String {
        val delim = context.indexOf('-')
        return if (delim < 0) {
            context
        } else {
            context.substring(0, delim)
        }
    }

    /**
     * Shutdown the server.
     */
    fun shutdownServer() {
        myServer.shutdown(false)
    }

    /**
     * Send a message to one or more providers based on whether they currently
     * host a particular context and/or user.
     *
     * @param omitProvider  One provider not to be sent to, or null if it
     * should be sent to all of them.
     * @param contextRef  The name of a context, or null if don't care.
     * @param userRef  The name of a user, or null if don't care.
     * @param msg  The message to send.
     *
     * @throws MessageHandlerException if there was a problem doing this.
     */
    fun targetedBroadCast(omitProvider: Provider?, contextRef: String?, userRef: String?, msg: JSONLiteral) {
        var context: OpenContext? = null
        var clones: HashSetMulti<OpenContext>? = null
        if (contextRef != null) {
            context = getContext(contextRef)
            if (context == null) {
                clones = contextClones(contextRef)
                if (clones.isEmpty) {
                    throw MessageHandlerException("context $contextRef not found")
                }
            }
        }
        var directorHasUser = false
        if (userRef != null) {
            directorHasUser = hasUser(userRef)
            if (!directorHasUser) {
                throw MessageHandlerException("user $userRef not found")
            }
        }
        if (context != null) {
            if (directorHasUser) {
                if (!context.hasUser(userRef!!)) {
                    throw MessageHandlerException("user $userRef is not in context $contextRef")
                }
            }
            val provider = context.provider()
            if (provider != omitProvider) {
                provider.actor().send(msg)
            }
        } else if (clones != null) {
            for (provider in myProviders.keys) {
                if (provider != omitProvider && provider.hasClone(contextRef!!)) {
                    if (userRef == null || provider.hasUser(userRef)) {
                        provider.actor().send(msg)
                    }
                }
            }
        } else if (directorHasUser) {
            for (provider in myProviders.keys) {
                if (provider != omitProvider && provider.hasUser(userRef!!)) {
                    provider.actor().send(msg)
                }
            }
        } else {
            throw MessageHandlerException(
                    "request message missing context or user")
        }
    }

    /**
     * Stop watching for the openings and closings of a context.
     *
     * @param contextName  The name of the context not to be watched.
     * @param admin  The administrator who no longer cares.
     */
    fun unwatchContext(contextName: String, admin: DirectorActor) {
        myWatchedContexts.remove(contextName, admin)
    }

    /**
     * Stop watching for the arrivals and departures of a user.
     *
     * @param userName  The name of the user not to be watched.
     * @param admin  The administrator who no longer cares.
     */
    fun unwatchUser(userName: String, admin: DirectorActor) {
        myWatchedUsers.remove(userName, admin)
    }

    /**
     * Lookup a user clone's contexts by name.
     *
     * @param userName  The name of the user context group sought.
     *
     * @return a multi-set of the contexts where clones of the user with the
     * given name appears.
     */
    fun userCloneContexts(userName: String) = myUserCloneSets.getMulti(userName)

    /**
     * Lookup a user's contexts by name.
     *
     * @param userName  The name of the user sought.
     *
     * @return a multi-set of the contexts where the user with the given name
     * appears.
     */
    fun userContexts(userName: String) = myUsers.getMulti(userName)

    /**
     * Get the names of known users.
     *
     * Return a set of the names of known users.
     */
    fun users() = myUsers.keys()

    /**
     * Watch for the openings and closings of a context.
     *
     * @param contextName  The name of the context to be watched.
     * @param admin  Who wants to know?
     */
    fun watchContext(contextName: String, admin: DirectorActor) {
        myWatchedContexts.add(contextName, admin)
    }

    /**
     * Watch for the arrivals and departures of a user.
     *
     * @param userName  The name of the user to be watched.
     * @param admin  Who wants to know?
     */
    fun watchUser(userName: String, admin: DirectorActor) {
        myWatchedUsers.add(userName, admin)
    }

    companion object {
        /** Value for myEstimatedLoadIncrement if not provided by configuration  */
        private const val DEFAULT_ESTIMATED_LOAD_INCREMENT = 0.0008

        /**
         * Test if a user name is the name of a user clone.
         *
         * @param userName  The user name to test.
         *
         * @return true if 'userName' is the name of a clone.  It is assumed to
         * be a clone name if it contains more than one '-' character.
         */
        @JvmStatic
        fun isUserClone(userName: String) = userName.indexOf('-') != userName.lastIndexOf('-')

        /**
         * Obtain the name of a user clone set from a user name.
         *
         * @param userName  The user name to get the clone set name from.
         *
         * @return the substring of 'userName' up to but not including the
         * second '-' character.
         */
        @JvmStatic
        fun userCloneSetName(userName: String): String {
            var dash = userName.indexOf('-')
            dash = userName.indexOf('-', dash + 1)
            return userName.substring(0, dash)
        }

        /**
         * Generate a 'relay' message.
         */
        private fun msgRelay(target: Referenceable, contextName: String?, userName: String?, relay: JsonObject) =
                JSONLiteralFactory.targetVerb(target, "relay").apply {
                    addParameterOpt("context", contextName)
                    addParameterOpt("user", userName)
                    addParameter("msg", relay)
                    finish()
                }
    }

    init {
        myRefTable.addRef(myProviderHandler)
        myRefTable.addRef("session", myProviderHandler)
        myRefTable.addRef(UserHandler(this, traceFactory))
        myAdminHandler = AdminHandler(this, traceFactory)
        myRefTable.addRef(myAdminHandler)
        myProviderLimit = myServer.props().intProperty("conf.director.providerlimit", 0)
        myEstimatedLoadIncrement = myServer.props().doubleProperty("conf.director.estloadbump", DEFAULT_ESTIMATED_LOAD_INCREMENT)
        myServer.registerShutdownWatcher {
            isShuttingDown = true
            val doomedProviders = LinkedList(myProviders.keys)
            myProviders.clear()
            for (provider in doomedProviders) {
                provider.actor().close()
            }
        }
    }
}
