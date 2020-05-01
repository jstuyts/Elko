package org.elkoserver.server.director

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonObject
import org.elkoserver.json.Referenceable
import org.elkoserver.util.trace.TraceFactory
import java.util.LinkedList

/**
 * Singleton handler for the director 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 * 'close' - Requests the closure of a particular context or disconnection of
 * a particular user.
 *
 * 'dump' - Requests a detailed description of the providers offering
 * services via this director, the contexts they are currently serving,
 * and the users currently in those contexts.  Optionally allows limiting
 * the scope of the query to particular families of contexts or users,
 * and limiting the level of detail in the information returned.
 *
 * 'find' - Requests the provider(s) serving a particular context or user.
 *
 * 'listcontexts' - Requests a list of currently active contexts.
 *
 * 'listproviders' - Requests a list of currently operational context
 * providers.
 *
 * 'listusers' - Requests a list of currently connected users.
 *
 * 'reinit' - Requests the director to order the reinitialization of zero or
 * more of the provider servers it knows about, and, optionally, itself.
 *
 * 'relay' - Requests the director to deliver an arbitrary message to a
 * context, context family, or user, by relaying through the appropriate
 * provider servers for the message target's current location.
 *
 * 'say' - Requests the director to deliver text in a 'say' message to a
 * context, context family, or user, by relaying through the appropriate
 * provider servers for the message target's current location.
 *
 * 'shutdown' - Requests the director to order the shut down of zero or more
 * of the provider servers it knows about, and, optionally, itself.  Also
 * has an option to force abrupt termination.
 *
 * 'unwatch' - Requests cancellation of an earlier 'watch' request.
 *
 * 'watch' - Requests the director to send notifications whenever a
 * particular context or context family opens or closes and/or whenever
 * a particular user arrives or departs.
 *
 * @param myDirector  The Director object for this handler.
 */
internal class AdminHandler(private val myDirector: Director, traceFactory: TraceFactory?) : BasicProtocolHandler(traceFactory) {

    /**
     * Do the actual work of a 'find' or 'watch' verb.
     *
     * @param watch  true if doing a watch, false if just a find.
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    private fun doFind(watch: Boolean, from: DirectorActor, context: OptString,
                       user: OptString) {
        val contextName = context.value(null)
        val userName = user.value(null)
        if (userName != null && contextName != null) {
            throw MessageHandlerException(
                    "context and user parameters are mutually exclusive")
        } else if (userName == null && contextName == null) {
            throw MessageHandlerException(
                    "context or user parameter missing")
        } else if (userName != null) {
            findUser(userName, from)
            if (watch) {
                from.admin()!!.watchUser(userName)
            }
        } else  /* if (contextName != null) */ {
            findContext(contextName, from)
            if (watch) {
                from.admin()!!.watchContext(contextName!!)
            }
        }
    }

    /**
     * Locate a context and send out information about it.
     *
     * @param contextName  The name of the context of interest.
     * @param admin  Who to send the information to.
     */
    fun findContext(contextName: String?, admin: DirectorActor) {
        val context = myDirector.getContext(contextName!!)
        if (context != null) {
            admin.send(msgContext(this, contextName, true,
                    context.provider().actor().label(), null))
        } else {
            val clones = myDirector.contextClones(contextName)
            if (!clones.isEmpty) {
                admin.send(msgContext(this, contextName, true, null,
                        encodeContexts(clones)))
            } else {
                admin.send(msgContext(this, contextName, false, null, null))
            }
        }
    }

    /**
     * Locate a user and send out information about it.
     *
     * @param userName  The name of the user of interest.
     * @param admin  Who to send the information to.
     */
    fun findUser(userName: String, admin: DirectorActor) {
        var contexts = myDirector.userContexts(userName)
        if (contexts.isEmpty) {
            contexts = myDirector.userCloneContexts(userName)
        }
        if (contexts.iterator().hasNext()) {
            admin.send(msgUser(this, userName, true,
                    encodeContexts(contexts)))
        } else {
            admin.send(msgUser(this, userName, false, null))
        }
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * know as 'admin'.
     *
     * @return a string referencing this object.
     */
    override fun ref(): String {
        return "admin"
    }

    /**
     * Handle the 'close' verb.
     *
     * Request that a user or context be terminated.
     *
     * @param from  The administrator asking for the closure.
     * @param context  The context to be closed.
     * @param user  The user to be closed.
     */
    @JSONMethod("context", "user")
    fun close(from: DirectorActor, context: OptString, user: OptString) {
        from.ensureAuthorizedAdmin()
        val contextName = context.value(null)
        val userName = user.value(null)
        val msg = msgClose(myDirector.providerHandler(), contextName,
                userName, false)
        myDirector.targetedBroadCast(null, contextName, userName, msg)
    }

    /**
     * Handle the 'dump' verb.
     *
     * Request a dump of the director's state.
     *
     * @param from  The administrator asking for the information.
     * @param depth  Depth limit for the dump.
     * @param provider  A provider to limit the dump to.
     * @param context  A context to limit the dump to.
     */
    @JSONMethod("depth", "provider", "context")
    fun dump(from: DirectorActor, depth: Int, provider: OptString, context: OptString) {
        from.ensureAuthorizedAdmin()
        val providerName = provider.value(null)
        val contextName = context.value(null)
        var numProviders = 0
        var numContexts = 0
        var numUsers = 0
        val providerList: MutableList<ProviderDump> = LinkedList()
        for (subj in myDirector.providers()) {
            if (providerName == null || subj.matchLabel(providerName)) {
                val providerDump = ProviderDump(depth, subj, contextName)
                if (providerDump.numContexts() > 0 || contextName == null) {
                    ++numProviders
                    numContexts += providerDump.numContexts()
                    numUsers += providerDump.numUsers()
                    if (depth > 0) {
                        providerList.add(providerDump)
                    }
                }
            }
        }
        from.send(msgDump(this, numProviders, numContexts, numUsers,
                providerList))
    }

    private class ProviderDump internal constructor(depth: Int, private val myProvider: Provider, contextName: String?) : Encodable {
        private var myNumContexts = 0
        private var myNumUsers = 0
        private val myOpenContexts = LinkedList<ContextDump>()
        fun numContexts(): Int {
            return myNumContexts
        }

        fun numUsers(): Int {
            return myNumUsers
        }

        override fun encode(control: EncodeControl) =
                JSONLiteralFactory.type("providerdesc", control).apply {
                    addParameter("provider", myProvider.actor().label())
                    addParameter("numcontexts", myNumContexts)
                    addParameter("numusers", myNumUsers)
                    addParameter("load", myProvider.loadFactor())
                    addParameter("capacity", myProvider.capacity())
                    addParameter("hostports", encodeStrings(myProvider.hostPorts()))
                    addParameter("protocols", encodeStrings(myProvider.protocols()))
                    addParameter("serving", encodeStrings(myProvider.services()))
                    if (myOpenContexts.size > 0) {
                        addParameter("contexts", encodeEncodableList(myOpenContexts))
                    }
                    finish()
                }

        init {
            for (context in myProvider.contexts()) {
                if (contextName == null || contextName == context.name()) {
                    val contextDump = ContextDump(depth, context)
                    ++myNumContexts
                    myNumUsers += context.userCount()
                    if (depth > 1) {
                        myOpenContexts.add(contextDump)
                    }
                }
            }
        }
    }

    private class ContextDump internal constructor(private val myDepth: Int, private val myContext: OpenContext) : Encodable {
        override fun encode(control: EncodeControl): JSONLiteral {
            val literal = JSONLiteralFactory.type("contextdesc", control)
            literal.addParameter("context", myContext.name())
            literal.addParameter("numusers", myContext.userCount())
            if (myDepth > 2) {
                literal.addParameter("users",
                        encodeStrings(myContext.users()))
            }
            literal.finish()
            return literal
        }

    }

    /**
     * Handle the 'find' verb.
     *
     * Request the location of a context or user.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    @JSONMethod("context", "user")
    fun find(from: DirectorActor, context: OptString, user: OptString) {
        from.ensureAuthorizedAdmin()
        doFind(false, from, context, user)
    }

    /**
     * Handle the 'listcontexts' verb.
     *
     * Request a list of the contexts currently open.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    fun listcontexts(from: DirectorActor) {
        from.ensureAuthorizedAdmin()
        from.send(
                msgListcontexts(this, encodeContexts(myDirector.contexts())))
    }

    /**
     * Handle the 'listproviders' verb.
     *
     * Request a list of the providers currently serving.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    fun listproviders(from: DirectorActor) {
        from.ensureAuthorizedAdmin()
        from.send(
                msgListproviders(this, encodeProviders(myDirector.providers())))
    }

    /**
     * Handle the 'listusers' verb.
     *
     * Request a list of the users currently online.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    fun listusers(from: DirectorActor) {
        from.ensureAuthorizedAdmin()
        from.send(msgListusers(this, encodeStrings(myDirector.users())))
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that one or more servers be reset.
     *
     * @param from  The administrator sending the message.
     * @param provider  The provider to be re-init'ed.
     * @param director  true if this director itself should be re-init'ed.
     */
    @JSONMethod("provider", "director")
    fun reinit(from: DirectorActor, provider: OptString, director: OptBoolean) {
        from.ensureAuthorizedAdmin()
        val providerName = provider.value(null)
        if (providerName != null) {
            val msg = msgReinit(myDirector.providerHandler())
            for (subj in myDirector.providers()) {
                if (providerName == "all" ||
                        subj.matchLabel(providerName)) {
                    subj.actor().send(msg)
                }
            }
        }
        if (director.value(false)) {
            myDirector.reinitServer()
        }
    }

    /**
     * Handle the 'relay' verb.
     *
     * Request that a message be relayed to a user or context.
     *
     * @param from  The administrator sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param msg  The message to relay to them.
     */
    @JSONMethod("context", "user", "msg")
    fun relay(from: DirectorActor, context: OptString, user: OptString, msg: JsonObject) {
        from.ensureAuthorizedAdmin()
        myDirector.doRelay(null, context, user, msg)
    }

    /**
     * Handle the 'say' verb.
     *
     * Request that text be sent to a user or context.
     *
     * @param from  The administrator sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param text  The message to send them.
     */
    @JSONMethod("context", "user", "text")
    fun say(from: DirectorActor, context: OptString, user: OptString, text: String) {
        from.ensureAuthorizedAdmin()
        val contextName = context.value(null)
        val userName = user.value(null)
        val msg = msgSay(myDirector.providerHandler(), contextName,
                userName, text)
        myDirector.targetedBroadCast(null, contextName, userName, msg)
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that one or more servers be shut down.
     *
     * @param from  The administrator sending the message.
     * @param provider  The provider(s) to be shut down, if any.
     * @param director  true if this director itself should be shut down.
     * @param optKill  True if provider(s) should be shutdown immediately instead
     * of cleaning up.
     */
    @JSONMethod("provider", "director", "kill")
    fun shutdown(from: DirectorActor, provider: OptString, director: OptBoolean, optKill: OptBoolean) {
        from.ensureAuthorizedAdmin()
        val providerName = provider.value(null)
        val kill = optKill.value(false)
        if (providerName != null) {
            val msg = msgShutdown(myDirector.providerHandler(), kill)
            for (subj in myDirector.providers()) {
                if (providerName == "all" ||
                        subj.matchLabel(providerName)) {
                    subj.actor().send(msg)
                }
            }
        }
        if (director.value(false)) {
            myDirector.shutdownServer()
        }
    }

    /**
     * Handle the 'unwatch' verb.
     *
     * Request a previously requested 'watch' be stopped.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context that was watched.
     * @param user  The user that was watched.
     */
    @JSONMethod("context", "user")
    fun unwatch(from: DirectorActor, context: OptString, user: OptString) {
        from.ensureAuthorizedAdmin()
        val contextName = context.value(null)
        val userName = user.value(null)
        if (contextName != null && userName != null) {
            throw MessageHandlerException(
                    "context and user parameters are mutually exclusive")
        } else if (contextName == null && userName == null) {
            throw MessageHandlerException(
                    "context or user parameter missing")
        } else if (contextName != null) {
            from.admin()!!.unwatchContext(contextName)
        } else  /* if (userName != null) */ {
            from.admin()!!.unwatchUser(userName!!)
        }
    }

    /**
     * Handle the 'watch' verb.
     *
     * Request notification about changes in the location of a context or
     * user.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    @JSONMethod("context", "user")
    fun watch(from: DirectorActor, context: OptString, user: OptString) {
        from.ensureAuthorizedAdmin()
        doFind(true, from, context, user)
    }

    companion object {
        /**
         * Generate a JSONLiteralArray of context names from a sequence of
         * OpenContext objects.
         */
        private fun encodeContexts(contexts: Iterable<OpenContext>) =
                JSONLiteralArray().apply {
                    for (context in contexts) {
                        addElement(context.name())
                    }
                    finish()
                }

        /**
         * Generate a JSONLiteralArray from a linked list of Encodable objects.
         */
        private fun encodeEncodableList(list: List<Encodable>) =
                JSONLiteralArray().apply {
                    for (elem in list) {
                        addElement(elem)
                    }
                    finish()
                }

        /**
         * Generate a JSONLiteralArray of provider labels from a set of provider
         * DirectorActor objects.
         */
        private fun encodeProviders(providers: Set<Provider>) =
                JSONLiteralArray().apply {
                    for (subj in providers) {
                        addElement(subj.actor().label())
                    }
                    finish()
                }

        /**
         * Generate a JSONLiteralArray of strings from a collection of strings.
         */
        private fun encodeStrings(strings: Collection<String>) =
                JSONLiteralArray().apply {
                    for (str in strings) {
                        addElement(str)
                    }
                    finish()
                }

        /**
         * Generate a 'close' message.
         */
        fun msgClose(target: Referenceable?, contextName: String?, userName: String?, isDup: Boolean) =
                JSONLiteralFactory.targetVerb(target, "close").apply {
                    addParameterOpt("context", contextName)
                    addParameterOpt("user", userName)
                    if (isDup) {
                        addParameter("dup", true)
                    }
                    finish()
                }

        /**
         * Generate a 'context' message.
         */
        private fun msgContext(target: Referenceable, contextName: String?, open: Boolean, provider: String?, clones: JSONLiteralArray?) =
                JSONLiteralFactory.targetVerb(target, "context").apply {
                    addParameter("context", contextName)
                    addParameter("open", open)
                    addParameterOpt("provider", provider)
                    addParameterOpt("clones", clones)
                    finish()
                }

        /**
         * Generate a 'dump' message.
         */
        private fun msgDump(target: Referenceable, numProviders: Int, numContexts: Int, numUsers: Int, providerList: List<ProviderDump>) =
                JSONLiteralFactory.targetVerb(target, "dump").apply {
                    addParameter("numproviders", numProviders)
                    addParameter("numcontexts", numContexts)
                    addParameter("numusers", numUsers)
                    if (providerList.size > 0) {
                        addParameter("providers", encodeEncodableList(providerList))
                    }
                    finish()
                }

        /**
         * Generate a 'listcontexts' message.
         */
        private fun msgListcontexts(target: Referenceable, contexts: JSONLiteralArray) =
                JSONLiteralFactory.targetVerb(target, "listcontexts").apply {
                    addParameter("contexts", contexts)
                    finish()
                }

        /**
         * Generate a 'listproviders' message.
         */
        private fun msgListproviders(target: Referenceable, providers: JSONLiteralArray) =
                JSONLiteralFactory.targetVerb(target, "listproviders").apply {
                    addParameter("providers", providers)
                    finish()
                }

        /**
         * Generate a 'listusers' message.
         */
        private fun msgListusers(target: Referenceable, users: JSONLiteralArray) =
                JSONLiteralFactory.targetVerb(target, "listusers").apply {
                    addParameter("users", users)
                    finish()
                }

        /**
         * Generate a 'reinit' message.
         */
        private fun msgReinit(target: Referenceable) =
                JSONLiteralFactory.targetVerb(target, "reinit").apply {
                    finish()
                }

        /**
         * Generate a 'say' message.
         */
        private fun msgSay(target: Referenceable, contextName: String, userName: String, text: String) =
                JSONLiteralFactory.targetVerb(target, "say").apply {
                    addParameterOpt("context", contextName)
                    addParameterOpt("user", userName)
                    addParameter("text", text)
                    finish()
                }

        /**
         * Generate a 'shutdown' message.
         */
        private fun msgShutdown(target: Referenceable, kill: Boolean) =
                JSONLiteralFactory.targetVerb(target, "shutdown").apply {
                    if (kill) {
                        addParameter("kill", true)
                    }
                    finish()
                }

        /**
         * Generate a 'user' message.
         */
        private fun msgUser(target: Referenceable, userName: String, online: Boolean, contexts: JSONLiteralArray?) =
                JSONLiteralFactory.targetVerb(target, "user").apply {
                    addParameter("user", userName)
                    addParameter("on", online)
                    addParameterOpt("contexts", contexts)
                    finish()
                }
    }
}
