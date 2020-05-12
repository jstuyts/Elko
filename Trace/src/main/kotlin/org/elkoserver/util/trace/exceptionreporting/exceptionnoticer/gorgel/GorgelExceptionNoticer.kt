package org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.gorgel

import org.elkoserver.util.trace.exceptionreporting.ExceptionNoticer
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag

class GorgelExceptionNoticer(private val gorgel: Gorgel) : ExceptionNoticer {
    private val fatalGorgel = gorgel.withAdditionalStaticTags(Tag("subLevel", "fatal"))

    override fun noticeReportedException(message: String, throwable: Throwable) {
        gorgel.error(message, throwable)
    }

    override fun noticeUncaughtException(message: String, throwable: Throwable) {
        fatalGorgel.error(message, throwable)
    }
}
