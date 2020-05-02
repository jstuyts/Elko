package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.AdminObject
import org.elkoserver.server.context.Contextor
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Internal object that acts as a client for the external 'echo' service.
 */
class EchoClient @JSONMethod constructor() : AdminObject(), Consumer<Any?> {
    /** Connection to the workshop running the echo service.  */
    private var myServiceLink: ServiceLink? = null

    /** Tag string indicating the current state of the service connection.  */
    private var myStatus = "startup"

    /** Ordered list of handlers for pending requests to the service.  */
    private val myResultHandlers = LinkedList<Consumer<Any>>()

    /**
     * Make this object live inside the context server.  In this case we
     * initiate a connection to the external echo service.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    override fun activate(ref: String, contextor: Contextor) {
        super.activate(ref, contextor)
        myStatus = "connecting"
        contextor.findServiceLink("echo", this)
    }

    /**
     * Callback that is invoked when the service connection is established or
     * fails to be established.
     *
     * @param obj  The connection to the echo service, or null if connection
     * setup failed.
     */
    override fun accept(obj: Any?) {
        if (obj != null) {
            myServiceLink = obj as ServiceLink?
            myStatus = "connected"
        } else {
            myStatus = "failed"
        }
    }

    /**
     * Issue an 'echo' request to the external service.
     *
     * @param text  The text to be echoed.
     * @param resultHandler  Handler to be invoked with the echoed string,
     * when it is received, or an error message string if there was a
     * problem.
     */
    fun probe(text: String?, resultHandler: Consumer<Any>) {
        val currentServiceLink = myServiceLink
        if (currentServiceLink != null) {
            myResultHandlers.addLast(resultHandler)
            val msg = JSONLiteralFactory.targetVerb("echotest", "echo").apply {
                addParameter("rep", this)
                addParameter("text", text)
                finish()
            }
            currentServiceLink.send(msg)
        } else {
            resultHandler.accept("no connection to echo service")
        }
    }

    /**
     * Get the current status of the connection to the external service.
     *
     * @return a tag string describing the current connection state.
     */
    fun status() = myStatus

    /**
     * Handler for the 'echo' message, which is a reply to earlier an echo
     * requests sent to the external service.
     */
    @JSONMethod("text")
    fun echo(from: ServiceActor, text: String) {
        val resultHandler = myResultHandlers.removeFirst()
        resultHandler.accept(text)
    }
}
