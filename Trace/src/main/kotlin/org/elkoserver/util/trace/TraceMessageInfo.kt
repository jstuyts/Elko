package org.elkoserver.util.trace

import java.time.Clock

internal class TraceMessageInfo(subsystem: String, level: Level, private val myText: String, clock: Clock) : TraceMessage(subsystem, level, clock) {
    override fun stringify(buffer: StringBuilder) {
        super.stringify(buffer)
        buffer.append(" : ")
        buffer.append(myText)
    }
}
