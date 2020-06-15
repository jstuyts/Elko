package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonObject
import org.elkoserver.server.context.Msg.msgExit
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton administrative object for entering and exiting contexts.
 *
 *
 * Unlike most objects, JSON messages may be sent to this object when the
 * user is in either the entered or exited state.  The behavior is somewhat
 * different in the two cases.  For example, when a user has not yet entered a
 * context, they can enter but not exit, and vice versa.  These states are
 * distinguished by whether the message seems to be arriving from a UserActor
 * (the pre-entry state) or from a User (the post-entry state).
 *
 * @param myContextor  The contextor for this session.
 */
class Session(private val myContextor: Contextor, private val password: String?, private val myGorgel: Gorgel, traceFactory: TraceFactory) : BasicProtocolHandler(traceFactory) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'session'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "session"

    /**
     * Handle the 'log' verb.
     *
     * Write the text given to the log.
     *
     * @param text  The text to log.
     */
    @JSONMethod("text")
    fun log(from: User, text: String) {
        myGorgel.info(text)
    }

    /**
     * Handle the 'dump' verb
     *
     * Return various blobs of information about the state of this context
     * server.
     *
     * @param what  Indicator of which type of information is desired
     * @param optContext  Optional context parameter, when relevant
     * @param testPassword  Password to verify that sender is allowed to do this
     */
    @JSONMethod("what", "password", "context")
    fun dump(from: Deliverer, what: String, testPassword: OptString, optContext: OptString) {
        val contextRef = optContext.value<String?>(null)
        if (password == null || password == testPassword.value<String?>(null)) {
            val reply = JSONLiteralFactory.targetVerb("session", "dump").apply {
                addParameter("what", what)
                when (what) {
                    "contexts" -> {
                        val list = JSONLiteralArray()
                        for (ctx in myContextor.contexts()) {
                            list.addElement(ctx.ref())
                        }
                        list.finish()
                        addParameter("contexts", list)
                    }
                    "users" -> {
                        val list = JSONLiteralArray()
                        myContextor.users()
                                .filter { contextRef == null || it.context().ref() == contextRef }
                                .forEach { list.addElement(it.ref()) }
                        list.finish()
                        addParameter("users", list)
                    }
                    "items" -> {
                    }
                    else -> addParameter("error", "unknown 'what' value: $what")
                }
                finish()
            }
            from.send(reply)
        }
    }

    /**
     * Handle the 'entercontext' verb.
     *
     * Enter the user who sent it into the context they asked for.
     *
     * @param user  The ID of the user who is entering.
     * @param name  The alleged name of the user who is entering.
     * @param context  Reference to the context they wish to enter.
     * @param contextTemplate  Optional reference to the template context from
     * which the context should be derived.
     * @param sess  Client session ID for the connection to the context.
     * @param auth  Authorization code for a reserved entry.
     * @param utag  Factory tag string for synthetic user
     * @param uparam  Arbitrary object parameterizing synthetic user
     * @param debug This session will use debug settings, if enabled.
     * @param scope  Application scope for filtering mods
     */
    @JSONMethod("user", "name", "context", "ctmpl", "sess", "auth", "utag", "?uparam", "debug", "scope")
    fun entercontext(from: Deliverer, user: OptString, name: OptString,
                     context: String, contextTemplate: OptString,
                     sess: OptString, auth: OptString, utag: OptString,
                     uparam: JsonObject?, debug: OptBoolean,
                     scope: OptString) {
        if (from is User) {
            throw MessageHandlerException("already in a context")
        }   /* if (from instanceof UserActor) */
        val fromActor = from as UserActor
        fromActor.enterContext(user.value<String?>(null), name.value<String?>(null), context,
                contextTemplate.value<String?>(null),
                sess.value<String?>(null), auth.value<String?>(null),
                utag.value<String?>(null), uparam,
                debug.value(false), scope.value<String?>(null))
    }

    /**
     * Handle the 'exit' verb.
     *
     * Exit the context and disconnect the user who sent it -- except that the
     * user isn't in any context, so there's nothing to do.
     */
    @JSONMethod
    fun exit(from: Deliverer) {
        throw MessageHandlerException("not in a context")
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Shutdown the server.
     *
     * @param testPassword  Password to verify that sender is allowed to do this.
     */
    @JSONMethod("password")
    fun shutdown(from: Deliverer, testPassword: OptString) {
        var fromUser: User? = null
        if (from is User) {
            fromUser = from
        }
        if (password == null || password == testPassword.value<String?>(null)) {
            myContextor.shutdownServer()
            if (fromUser != null) {
                fromUser.exitContext("server shutting down", "shutdown", false)
            } else {
                from.send(msgExit(this, "server shutting down", "shutdown", false))
                (from as Actor).close()
            }
        } else {
            if (fromUser != null) {
                fromUser.exitContext(null, null, false)
            } else {
                (from as Actor).close()
            }
        }
    }
}
