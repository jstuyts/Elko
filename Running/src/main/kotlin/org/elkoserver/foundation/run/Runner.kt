package org.elkoserver.foundation.run

/**
 * Runs when it can, but never on empty.  A thread services a queue
 * of Runnables.
 */
interface Runner {
    fun enqueue(todo: Runnable)
}
