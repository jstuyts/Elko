package org.elkoserver.util.trace

import java.time.Clock
import java.util.*

open class TraceMessage(private val mySubsystem: String, private val myLevel: Level, clock: Clock) {

    val timestamp = clock.millis()

    override fun toString(): String {
        val buffer = StringBuilder(200)
        stringify(buffer)
        return buffer.toString()
    }

    open fun stringify(buffer: StringBuilder) {
        buffer.setLength(0)
        val formatter = Formatter(buffer)
        formatter.format("- %1\$tY/%1\$tm/%1\$td %1\$tT.%1\$tL ", timestamp)
        buffer.append(myLevel.terseCode)
        buffer.append(' ')
        buffer.append(mySubsystem)
    }
}
