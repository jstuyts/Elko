package org.elkoserver.foundation.run

import java.util.concurrent.Callable

/**
 * Used for Runner.now()
 *
 * If non-null, it is the thunk to be executed.
 *
 * Iff null, then we assume the thunk has been executed.
 */
internal class NowRunnable(private var myOptTodo: Callable<Any>?) : Runnable {
    /**
     * Acts as a condition variable on "null == myOptTodo"
     */
    private val myLock = Object()

    /**
     * If myOptTodo threw a problem, then this is the problem.
     *
     *
     *
     * Meaningful iff null == myOptTodo.  If null, then myResult is the
     * successful return value.
     */
    private var myOptProblem: Throwable? = null

    /**
     * Meaningful iff null == myOptTodo && null == myOptProblem, in which
     * case it's the value successfully returned by myOptTodo's execution.
     */
    private var myResult: Any? = null

    /**
     * Called in the thread doing the now().
     *
     *
     *
     * Schedules the execution of myOptTodo as a turn of the RunnerThread.
     * Blocks until that thunk completes in the RunnerThread, at which point
     * the outcome of myOptTodo becomes the outcome of the runFrom() (and
     * therefore the outcome of the now()).
     */
    fun runNow(): Any? {
        synchronized(myLock) {
            while (null != myOptTodo) {
                try {
                    myLock.wait()
                } catch (ie: InterruptedException) {
                    /* FIXME. Do not ignore. Old comment: Ignore interrupt & continue waiting for condition */
                }
            }
        }
        myOptProblem?.let { throw asSafe(it) }
        return myResult
    }

    /**
     * Called in the RunnerThread.
     */
    override fun run() {
        try {
            myResult = myOptTodo!!.call()
        } catch (problem: Throwable) {
            myOptProblem = problem
        }
        myOptTodo = null
        synchronized(myLock) { myLock.notifyAll() }
    }

    override fun toString() = "${super.toString()}: $myOptTodo"

    companion object {
        /**
         * Wrap a [Throwable] in a [RuntimeException].
         *
         * Wraps `problem` if necessary so that the caller can do a
         * <pre>
         * throw ExceptionMgr.asSafe(problem);
        </pre> *
         *
         * without having to declare any new "throws" cases.  The caller does the
         * throw rather than this method so that the Java compiler will have better
         * control flow information.
         *
         * @param problem  The [Throwable] to wrap
         */
        private fun asSafe(problem: Throwable): RuntimeException {
            return if (problem is RuntimeException) { problem } else RuntimeException(problem)
        }
    }
}
