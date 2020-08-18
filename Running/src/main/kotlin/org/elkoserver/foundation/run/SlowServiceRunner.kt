package org.elkoserver.foundation.run

import java.util.concurrent.Callable
import java.util.function.Consumer

/**
 * This class provides a mechanism for safely making use of external services
 * that are only available via synchronous, blocking (i.e., slow) interfaces.
 * It maintains a thread pool in which calls to such services run, delivering
 * their results back via callback thunks that are dropped onto the normal
 * server run queue.
 */
interface SlowServiceRunner {
    fun enqueueTask(task: Callable<Any?>, resultHandler: Consumer<Any?>?)
}
