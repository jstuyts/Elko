package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.ContextShutdownWatcher
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import org.elkoserver.util.trace.Trace

/**
 * Context mod to test ZMQ outbound connections
 */
class ZMQSendTester @JSONMethod("address") constructor(private val myAddress: String) : Mod(), ContextMod, ObjectCompletionWatcher, ContextShutdownWatcher {

    /** Outbound ZMQ connection to myAddress, or null if not yet open  */
    private var myOutbound: Connection? = null

    /** Flag to interlock connection close and context shutdown  */
    private var amDead = false

    /** Trace object for logging  */
    private lateinit var tr: Trace

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
                JSONLiteralFactory.type("zmqsendtest", control).apply {
                    addParameter("address", myAddress)
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
    fun log(from: User, str: String) {
        ensureInContext(from)
        if (myOutbound != null) {
            val msg = JSONLiteralFactory.targetVerb("logger", "log").apply {
                addParameter("str", str)
                finish()
            }
            myOutbound!!.sendMsg(msg)
        } else {
            tr.errorm("received 'log' request before outbound connection ready")
        }
    }

    override fun objectIsComplete() {
        context().registerContextShutdownWatcher(this)
        val contextor = `object`().contextor()
        tr = contextor.tr.subTrace("zmq")
        if (true) {
            throw IllegalStateException()
        }
//        contextor.server.networkManager.connectVia(
//                "org.elkoserver.foundation.net.zmq.ZeroMQConnectionManager",
//                "",  // XXX propRoot, needs to come from somewhere
//                myAddress,
//                object : MessageHandlerFactory {
//                    override fun provideMessageHandler(connection: Connection?): MessageHandler? {
//                        if (amDead) {
//                            connection!!.close()
//                        } else {
//                            myOutbound = connection
//                        }
//                        return NullMessageHandler(tr)
//                    }
//                },
//                tr)
    }

    override fun noteContextShutdown() {
        if (!amDead) {
            amDead = true
            myOutbound?.close()
        }
    }
}
