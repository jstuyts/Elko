package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import java.util.function.Consumer

/**
 * Mod to enable a context user to exercise the external 'echo' service.
 */
class EchoMod @JsonMethod constructor() : Mod(), ContextMod, ObjectCompletionWatcher {
    /** The internal object that acts as the client for the echo service.  */
    private var myService: EchoClient? = null

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral {
        val result = JsonLiteralFactory.type("echomod", control)
        result.finish()
        return result
    }

    /**
     * Message handler for the 'echo' message.  This invokes the external
     * echo service, if possible.
     */
    @JsonMethod("text")
    fun echo(from: User, text: String) {
        ensureSameContext(from)
        val currentService = myService
        if (currentService != null) {
            currentService.probe(text, Consumer { obj ->
                val msg = JsonLiteralFactory.targetVerb(`object`(), "echo").apply {
                    addParameter("text", obj)
                    finish()
                }
                from.send(msg)
            })
        } else {
            val msg = JsonLiteralFactory.targetVerb(`object`(), "echo").apply {
                addParameter("error", "no service")
                finish()
            }
            from.send(msg)
        }
    }

    /**
     * Message handler for the 'status' message.  This requests a report on
     * the state of the echo client connection.
     */
    @JsonMethod
    fun status(from: User) {
        ensureSameContext(from)
        val msg = JsonLiteralFactory.targetVerb(`object`(), "status").apply {
            addParameter("status", myService?.status ?: "no service")
            finish()
        }
        from.send(msg)
    }
    /* ----- ObjectCompletionWatcher interface ----- */
    /**
     * Take notice that the associated context is complete.  In this case, we
     * lookup the echo client object from the environment, now that (since
     * the context is complete) there is an environment to look it up from.
     */
    override fun objectIsComplete() {
        myService = `object`().contextor().getStaticObject("echoclient") as EchoClient?
    }
}
