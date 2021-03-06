package org.elkoserver.foundation.net.zmq.test

import org.elkoserver.foundation.json.ClassspecificGorgelUsingObject
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.zmq.ZeromqOutbound
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.ContextMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.ObjectCompletionWatcher
import org.elkoserver.server.context.model.User
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Context mod to test ZMQ outbound connections
 */
class ZeromqSendMod @JsonMethod("outbound") constructor(private val myOutboundName: String) : Mod(), ContextMod,
    ObjectCompletionWatcher, ClassspecificGorgelUsingObject {

    /** The connection to send outbound messages on  */
    private var myConnection: Connection? = null

    private lateinit var myGorgel: Gorgel

    override fun setGorgel(gorgel: Gorgel) {
        myGorgel = gorgel
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral? =
            if (!control.toClient()) {
                JsonLiteralFactory.type("zmqsendmod", control).apply {
                    addParameter("outbound", myOutboundName)
                    finish()
                }
            } else {
                null
            }

    /**
     * Handle the 'log' verb.  Logs an arbitrary string to the ZMQ connection
     *
     * @param str  String to log
     */
    @JsonMethod("str")
    fun log(from: User, str: String?) {
        ensureInContext(from)
        val currentConnection = myConnection
        if (currentConnection != null) {
            val msg = JsonLiteralFactory.targetVerb("logger", "log").apply {
                addParameter("str", str)
                finish()
            }
            currentConnection.sendMsg(msg)
        } else {
            myGorgel.error("uninitialized outbound connection")
        }
    }

    override fun objectIsComplete() {
        val contextor = `object`().contextor()
        val outbound = contextor.getStaticObject(myOutboundName) as ZeromqOutbound
        myConnection = outbound.connection
    }
}
