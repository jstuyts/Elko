package org.elkoserver.foundation.net.zmq.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.zmq.ZMQOutbound
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import org.elkoserver.util.trace.Trace

/**
 * Context mod to test ZMQ outbound connections
 */
class ZMQSendMod @JSONMethod("outbound") constructor(private val myOutboundName: String) : Mod(), ContextMod, ObjectCompletionWatcher {

    /** The connection to send outbound messages on  */
    private var myConnection: Connection? = null

    /** Trace object for logging  */
    private var tr: Trace? = null

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            if (!control.toClient()) {
                JSONLiteralFactory.type("zmqsendmod", control).apply {
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
    @JSONMethod("str")
    fun log(from: User?, str: String?) {
        ensureInContext(from!!)
        val currentConnection = myConnection
        if (currentConnection != null) {
            val msg = JSONLiteralFactory.targetVerb("logger", "log").apply {
                addParameter("str", str)
                finish()
            }
            currentConnection.sendMsg(msg)
        } else {
            tr!!.errorm("uninitialized outbound connection")
        }
    }

    override fun objectIsComplete() {
        val contextor = `object`().contextor()
        tr = contextor.appTrace()
        val outbound = contextor.getStaticObject(myOutboundName) as ZMQOutbound
        myConnection = outbound.connection
    }
}
