package org.elkoserver.server.repository

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.objdb.ObjectStoreFactory.createAndInitializeObjectStore
import org.elkoserver.util.trace.Trace
import org.elkoserver.util.trace.TraceFactory
import java.time.Clock

/**
 * Main state data structure in a Repository.
 *
 * @param myServer Server object.
 * @param appTrace  Trace object for diagnostics.
 */
internal class Repository(private val myServer: Server, appTrace: Trace, traceFactory: TraceFactory, clock: Clock) {
    /** Table for mapping object references in messages.  */
    internal val myRefTable = RefTable(AlwaysBaseTypeResolver, traceFactory, clock)

    /** Local object storage module.  */
    internal val myObjectStore = createAndInitializeObjectStore(myServer.props(), "conf.rep", appTrace)

    /** Number of repository clients currently connected.  */
    private var myRepClientCount = 0

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    /** Flag that is set once server shutdown begins.  */
    var isShuttingDown = false

    /**
     * Add to or subtract from the number of repository clients connected.  If
     * the number drops to zero and this server is in the midst of shutting
     * down, terminate the object store.
     *
     * @param delta  The amount to change the count by.
     */
    fun countRepClients(delta: Int) {
        myRepClientCount += delta
        if (isShuttingDown && myRepClientCount <= 0) {
            myObjectStore.shutdown()
        }
    }

    /**
     * Reinitialize the server.
     */
    fun reinit() {
        myServer.reinit()
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    fun shutdown(kill: Boolean) {
        myServer.shutdown(kill)
    }

    init {
        myRefTable.addRef(RepHandler(this, traceFactory))
        myRefTable.addRef(AdminHandler(this, traceFactory))
        myServer.registerShutdownWatcher(object : ShutdownWatcher {
            override fun noteShutdown() {
                isShuttingDown = true
                countRepClients(0)
            }
        })
    }
}
