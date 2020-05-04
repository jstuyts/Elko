package org.elkoserver.foundation.run

/**
 * This peculiar exception is also a Runnable that when run() throws
 * itself.  It exists as part of the implementation of Runner to
 * enable it to do a no-overhead orderly shutdown.
 */
internal class ShutdownRunQException : RuntimeException, Runnable {
    constructor() {}
    constructor(m: String?) : super(m) {}

    override fun run() {
        throw this
    }
}
