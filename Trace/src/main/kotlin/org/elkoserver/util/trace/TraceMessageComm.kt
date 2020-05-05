package org.elkoserver.util.trace

import java.time.Clock

internal class TraceMessageComm(subsystem: String, level: Level,
                                private val myConn: String,
                                private val amInbound: Boolean,
                                private val myMsg: String, clock: Clock) : TraceMessage(subsystem, level, clock) {

    override fun stringify(buffer: StringBuilder) {
        super.stringify(buffer)
        buffer.append(" : ")
        buffer.append(myConn)
        buffer.append(if (amInbound) " ->" else " <-")
        if (myMsg[0] != ' ') {
            buffer.append(' ')
        }
        buffer.append(myMsg)
    }
}
