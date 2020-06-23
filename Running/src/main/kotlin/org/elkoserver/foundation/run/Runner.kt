package org.elkoserver.foundation.run

import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.util.concurrent.Callable

/**
 * Runs when it can, but never on empty.  A thread services a queue
 * of Runnables.
 *
 * @param name is the name to give to the thread created.
 */
class Runner(name: String, traceFactory: TraceFactory) : Runnable {
    private val tr: Trace = traceFactory.trace("runner")

    /**
     * Note that Queue is a thread-safe data structure with its own lock.
     */
    private val myQ = Queue<Runnable>()

    /**
     * Normally, myWorker == myThread.
     * While another thread is synchronously calling into this runner and
     * holding myRunLock, that thread is remembered in myWorker so it
     * can be interrupted instead of myThread.
     */
    private val myWorker: Thread

    /**
     * The lock protecting the heap of objects "inside" the runner.  All
     * application execution inside the server must only happen while this
     * lock is held.
     */
    private val myRunLock = Any()

    /**
     * When the queue is empty, myThread blocks on myNotifyLock rather
     * than myRunLock to avoid a peculiar deadlock possibility: In
     * java, one cannot notify() a lock without grabbing it, and
     * enqueue() needs to notify the lock that myThread is wait()ing
     * on.  This should be fine, as enqueue() only notify()s when
     * myThread is waiting, and therefore not holding the runLock.
     * However, a now() may be holding the runLock.  If that now()
     * blocks waiting on a lock held by enqueue()'s caller, we
     * deadlock.  It would be too hard to explain or remember what not
     * to do to avoid this deadlock.  Hence a separate lock.  Ugh.
     */
    private val myNotifyLock = Object()
    private var myNeedsNotify = false

    /**
     * Makes a Runner, and starts the thread that services its queue.
     * The name of the thread will be "Elko RunQueue".
     */
    constructor(traceFactory: TraceFactory) : this("Elko RunQueue", traceFactory)

    /**
     * Queues something for this Runnable's thread to do.  May be called
     * from any thead.
     */
    fun enqueue(todo: Runnable?) {
        /* enqueueing is guarded by the queue's lock, not the runLock. */
        myQ.enqueue(todo)
        if (myNeedsNotify) {
            /* even here, enqueue() avoids grabbing the runLock. */
            synchronized(myNotifyLock, myNotifyLock::notify)
        }
    }

    /**
     * Tests whether the current thread is holding the run lock
     */
    val isCurrentThreadInRunner: Boolean
        get() = Thread.currentThread() === myWorker

    /**
     * Schedules a thunk to execute "inside" this runner (in the RunnerThread
     * as a separate turn while holding the runLock), while also effectively
     * executing as a synchronous call within the requestors's thread.
     *
     * In most ways this can be thought of as a symmetric rendezvous between
     * the two Threads.  The reason we *specify* that the thunk is executed
     * specifically in the requested runner's RunnerThread is so that
     * thread-scoped state, such as Runner.currentRunner(), will be according
     * to the Runner receiving the now() request, not whatever thread made
     * the request.
     */
    fun now(todo: Callable<Any>): Any? {
        val nr = NowRunnable(todo)
        enqueue(nr)
        return nr.runNow()
    }

    /**
     * Will enqueue a request to shut down this runner's thread.  Since
     * this is an enqueued request, the thread will only shut down
     * after finishing earlier requests, as well as any now()s that
     * happen in the meantime.
     */
    fun orderlyShutdown() {
        enqueue(Runnable { mustStop = true})
    }

    private var mustStop = false

    /**
     * Called only by [Thread.start].  Pulls Runnables off of the queue
     * until there aren't any more, then waits until there's more to do.
     */
    override fun run() {
        var msgCount = 0
        while (!mustStop) {
            try {
                Thread.yield()
                var todo: Runnable? = null
                synchronized(myRunLock) {
                    for (i in 0 until DEQUEUE_GRANULARITY) {
                        /* This call to optDequeue() will momentarily acquire
                           the queue lock while we're still holding the
                           runLock!  I believe this is safe under all
                           conditions, but cannot prove it at this time. */
                        val nextTodo = myQ.optDequeue()
                        todo = nextTodo
                        if (nextTodo == null) {
                            break
                        }
                        nextTodo.run()
                        ++msgCount
                    }
                }
                if (todo == null) {
                    /* We can't sleep by waiting on the runLock (see myNotifyLock)
                               but we have to release the runLock while we're asleep, so
                               our sleepage logic is here, outside the above synchronized
                               block. */
                    synchronized(myNotifyLock) {

                        /* More elements could have arrived in the meantime, so
                                   check again after grabbing myNotifyLock. */
                        if (!myQ.hasMoreElements()) {
                            if (tr.debug) {
                                tr.debugm("RunQ empty after $msgCount messages.  sleeping now.")
                            }
                            msgCount = 0
                            myNeedsNotify = true
                            try {
                                waitForMore()
                            } catch (e: InterruptedException) {
                                /* FIXME. Do not ignore. */
                            } finally {
                                myNeedsNotify = false
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                tr.errorReportException(t, "Exception made it all the way out of the run loop.  Restarting it.")
            }
        }
    }

    /**
     * Called by run() when we're out of messages in the queue, or
     * after the debug hook does its biz.  Must only while
     * myNotifyLock is held.
     *
     * May be overridden in a subclass to wake up occasionally to do
     * background things, like checking for finalizers.  The implementation
     * here simply wait()s on myNotifyLock.
     */
    @Throws(InterruptedException::class)
    private fun waitForMore() {
        myNotifyLock.wait(100L)
    }

    companion object {
        /**
         * The number of Runnables to dequeue and run in one go.
         * Must be >= 1.
         */
        private const val DEQUEUE_GRANULARITY = 25

        /**
         * Utility routine to either swallow or throw exceptions, depending on
         * whether or not they are the kind of exceptions that need to escape from
         * the run loop.
         */
        fun throwIfMandatory(t: Throwable) {
            if (t is VirtualMachineError) {
                throw t
            }
            if (t is ThreadDeath) {
                throw t
            }
            if (t is LinkageError) {
                throw t
            }
        }
    }

    init {
        myWorker = RunnerThread(this, name)
        myWorker.start()
    }
}
