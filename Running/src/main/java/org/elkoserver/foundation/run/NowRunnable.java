package org.elkoserver.foundation.run;

import java.util.concurrent.Callable;

/**
 * Used for Runner.now()
 */
class NowRunnable implements Runnable {

    /**
     * Acts as a condition variable on "null == myOptTodo"
     */
    private final Object myLock = new Object();

    /**
     * If non-null, it is the thunk to be executed.  <p>
     *
     * Iff null, then we assume the thunk has been executed.
     */
    private Callable<Object> myOptTodo;

    /**
     * If myOptTodo threw a problem, then this is the problem.<p>
     *
     * Meaningful iff null == myOptTodo.  If null, then myResult is the
     * successful return value.
     */
    private Throwable myOptProblem;

    /**
     * Meaningful iff null == myOptTodo && null == myOptProblem, in which
     * case it's the value successfully returned by myOptTodo's execution.
     */
    private Object myResult;

    /**
     *
     */
    NowRunnable(Callable<Object> todo) {
        myOptTodo = todo;
    }

    /**
     * Called in the thread doing the now().<p>
     *
     * Schedules the execution of myOptTodo as a turn of the RunnerThread.
     * Blocks until that thunk completes in the RunnerThread, at which point
     * the outcome of myOptTodo becomes the outcome of the runFrom() (and
     * therefore the outcome of the now()).
     */
    Object runNow() {
        synchronized (myLock) {
            while (null != myOptTodo) {
                try {
                    myLock.wait();
                } catch (InterruptedException ie) {
                    /* Ignore interrupt & continue waiting for condition */
                }
            }
        }
        if (null != myOptProblem) {
            throw asSafe(myOptProblem);
        }
        return myResult;
    }

    /**
     * Wrap a {@link Throwable} in a {@link RuntimeException}.
     *
     * Wraps <tt>problem</tt> if necessary so that the caller can do a
     * <pre>
     * throw ExceptionMgr.asSafe(problem);
     * </pre>
     *
     * without having to declare any new "throws" cases.  The caller does the
     * throw rather than this method so that the Java compiler will have better
     * control flow information.
     *
     * @param problem  The [Throwable] to wrap
     */
    private static RuntimeException asSafe(Throwable problem) {
        if (problem instanceof  RuntimeException) {
            return (RuntimeException) problem;
        }
        return new RuntimeException(problem);
    }

    /**
     * Called in the RunnerThread.
     */
    public void run() {
        try {
            myResult = myOptTodo.call();
        } catch (Throwable problem) {
            myOptProblem = problem;
        }
        myOptTodo = null;
        synchronized (myLock) {
            myLock.notifyAll();
        }
    }

    public String toString() {
        return super.toString() + ": " + myOptTodo;
    }
}
