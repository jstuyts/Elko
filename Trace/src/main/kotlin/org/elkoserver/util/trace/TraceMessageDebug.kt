package org.elkoserver.util.trace

import java.time.Clock

internal class TraceMessageDebug(subsystem: String, level: Level, frameData: StackTraceElement?,
                                 private val myText: String, clock: Clock) : TraceMessage(subsystem, level, clock) {
    private val myFileName: String

    private val myMethodName: String

    private val myLine: String

    override fun stringify(buffer: StringBuilder) {
        super.stringify(buffer)
        buffer.append(" (")
        buffer.append(myMethodName)
        buffer.append(':')
        buffer.append(myFileName)
        buffer.append(':')
        buffer.append(myLine)
        buffer.append(") ")
        buffer.append(myText)
    }

    init {
        if (frameData != null) {
            val className = frameData.className
            val dotPos = className.lastIndexOf('.')
            myMethodName = if (dotPos >= 0) {
                className.substring(dotPos + 1) + '.' +
                        frameData.methodName
            } else {
                frameData.methodName
            }
            myFileName = frameData.fileName ?: "file?"
            myLine = frameData.lineNumber.toString()
        } else {
            myMethodName = "method?"
            myFileName = "file?"
            myLine = "line?"
        }
    }
}
