package org.elkoserver.foundation.boot

import org.elkoserver.util.trace.exceptionreporting.ExceptionNoticer
import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import kotlin.test.Test
import kotlin.test.assertSame

class EMThreadGroupTest {
    @Test
    fun `exception in thread is forwarded to exception reporter`() {
        val forwardedThrowableHolder = ThrowableHolder()
        val group = EMThreadGroup("Name", ExceptionReporter(object : ExceptionNoticer {
            override fun noticeReportedException(message: String, throwable: Throwable) {
                // No action needed. The throwable is passed wrapped in another throwable to this function.
            }

            override fun noticeUncaughtException(message: String, throwable: Throwable) {
                forwardedThrowableHolder.throwable = throwable
            }
        }))
        val throwable = Throwable()
        val exceptionThrowingThread = Thread(group) {
            throw throwable
        }

        exceptionThrowingThread.run {
            start()
            join()
        }

        assertSame(throwable, forwardedThrowableHolder.throwable)
    }
}
