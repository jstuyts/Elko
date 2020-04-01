package org.elkoserver.foundation.net.zmq;

/**
 * An arbitrary (zero-argument) executable.  Similar to {@link Runnable} but
 * can throw an exception.
 *
 * @see java.lang.Runnable
 */
public interface Thunk {
    /**
     * Execute this thunk.
     */
    void run() throws Throwable;
}
