package org.elkoserver.foundation.boot

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock

/**
 * Interface to be implemented by application classes that want to be launched
 * by `Boot`.
 */
interface Bootable {
    /**
     * The method that `org.elkoserver.foundation.boot.Boot` calls to
     * start the application.
     *
     * @param props  Properties specified by the command line, environment
     * variables, and property files.
     */
    fun boot(props: ElkoProperties, traceFactory: TraceFactory, clock: Clock)
}
