package org.elkoserver.util.trace

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Clock

internal class TraceMessageDebug(subsystem: String, level: Level, frameData: StackTraceElement?,
                                 private val myText: String,
                                 private val myObject: Any?, clock: Clock) : TraceMessage(subsystem, level, clock) {
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
        if (myObject != null) {
            if (myObject is Throwable) {
                buffer.append('\n')
                StringWriter().use { stringWriter ->
                    PrintWriter(stringWriter).use { printWriter ->
                        printStackTrace(myObject, printWriter)
                    }
                    buffer.append(stringWriter.toString())
                }
            } else {
                buffer.append(" : ")
                buffer.append(myObject)
            }
        }
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
            myLine = "" + frameData.lineNumber
        } else {
            myMethodName = "method?"
            myFileName = "file?"
            myLine = "line?"
        }
    }
}
