package org.elkoserver.foundation.run.singlethreadexecutor

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Runs when it can, but never on empty.  A thread services a queue
 * of Runnables.
 *
 * @param name is the name to give to the thread created.
 */
class SingleThreadExecutorRunner(name: String) : Executor {

    private val executorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name) }

    /**
     * Makes a Runner, and starts the thread that services its queue.
     * The name of the thread will be "Elko RunQueue".
     */
    constructor() : this("Elko RunQueue")

    /**
     * Queues something for this Runnable's thread to do.  May be called
     * from any thead.
     */
    override fun execute(command: Runnable) {
        executorService.execute(command)
    }

    /**
     * Will enqueue a request to shut down this runner's thread.  Since
     * this is an enqueued request, the thread will only shut down
     * after finishing earlier requests, as well as any now()s that
     * happen in the meantime.
     */
    fun orderlyShutdown() {
        executorService.apply {
            shutdown()
            awaitTermination(5L, SECONDS)
        }
    }
}
