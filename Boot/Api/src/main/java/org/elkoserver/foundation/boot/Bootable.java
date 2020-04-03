package org.elkoserver.foundation.boot;

import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.util.trace.TraceFactory;

import java.time.Clock;

/**
 * Interface to be implemented by application classes that want to be launched
 * by <code>Boot</code>.
 */
public interface Bootable {
    /**
     * The method that <code>org.elkoserver.foundation.boot.Boot</code> calls to
     * start the application.
     *
     * @param props  Properties specified by the command line, environment
     *    variables, and property files.
     */
    void boot(ElkoProperties props, TraceFactory traceFactory, Clock clock);
}
