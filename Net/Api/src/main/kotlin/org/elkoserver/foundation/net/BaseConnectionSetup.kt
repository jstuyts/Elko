package org.elkoserver.foundation.net

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException

abstract class BaseConnectionSetup(
        label: String?,
        val host: String,
        private val auth: AuthDesc,
        val secure: Boolean,
        props: ElkoProperties,
        val propRoot: String,
        private val gorgel: Gorgel,
        protected var traceFactory: TraceFactory) : ConnectionSetup {
    val bind: String = props.getProperty("$propRoot.bind", host)

    var msgTrace = if (label != null) {
        traceFactory.comm.subTrace(label)
    } else {
        traceFactory.comm.subTrace("cli")
    }

    override fun startListener(): NetAddr {
        val result: NetAddr
        try {
            result = tryToStartListener()
            gorgel.i?.run { info("listening for $protocol connections$connectionsSuffixForNotice on $listenAddressDescription${(if (bind != valueToCompareWithBind) " ($bind)" else "")} (${auth.mode})${if (secure) " (secure mode)" else ""}") }
        } catch (e: IOException) {
            gorgel.error("unable to open $protocol$protocolSuffixForErrorMessage listener $propRoot on requested host: $e")
            throw IllegalStateException()
        }
        return result
    }

    @Throws(IOException::class)
    abstract fun tryToStartListener(): NetAddr
    abstract val listenAddressDescription: String
    abstract val valueToCompareWithBind: String
    open val connectionsSuffixForNotice: String
        get() = ""

    open val protocolSuffixForErrorMessage: String
        get() = ""

}
