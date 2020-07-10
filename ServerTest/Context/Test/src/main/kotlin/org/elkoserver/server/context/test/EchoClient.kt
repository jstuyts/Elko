package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.AdminObject
import org.elkoserver.server.context.Contextor
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Internal object that acts as a client for the external 'echo' service.
 */
class EchoClient @JsonMethod constructor() : AdminObject(), Consumer<ServiceLink?> {
    /** Connection to the workshop running the echo service.  */
    private var myServiceLink: ServiceLink? = null

    /** Tag string indicating the current state of the service connection.  */
    internal var status = "startup"
        private set

    /** Ordered list of handlers for pending requests to the service.  */
    private val myResultHandlers = LinkedList<Consumer<in String>>()

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
        status = "connecting"
        contextor.findServiceLink("echo", this)
    }

    /**
     * Callback that is invoked when the service connection is established or
     * fails to be established.
     *
     * @param obj  The connection to the echo service, or null if connection
     * setup failed.
     */
    override fun accept(obj: ServiceLink?) {
        if (obj != null) {
            myServiceLink = obj
            status = "connected"
        } else {
            status = "failed"
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
    fun probe(text: String?, resultHandler: Consumer<in String>) {
        val currentServiceLink = myServiceLink
        if (currentServiceLink != null) {
            myResultHandlers.addLast(resultHandler)
            val msg = JsonLiteralFactory.targetVerb("echotest", "echo").apply {
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
     * Handler for the 'echo' message, which is a reply to earlier an echo
     * requests sent to the external service.
     */
    @JsonMethod("text")
    fun echo(from: ServiceActor, text: String) {
        val resultHandler = myResultHandlers.removeFirst()
        resultHandler.accept(text)
    }
}
