package org.elkoserver.util

/**
 * Utility routine to either swallow or throw exceptions, depending on
 * whether or not they are the kind of exceptions that need to escape from
 * the run loop.
 */
fun throwIfMandatory(t: Throwable) {
    if (t is VirtualMachineError || t is ThreadDeath || t is LinkageError) {
        throw t
    }
}
