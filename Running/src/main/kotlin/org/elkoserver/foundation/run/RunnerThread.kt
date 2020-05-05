package org.elkoserver.foundation.run

/**
 * Makes a Runnable into a Thread-scoped variable.
 */
internal class RunnerThread(var myRunnable: Runnable, name: String?) : Thread(myRunnable, name)