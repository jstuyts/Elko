package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.AlwaysBaseTypeResolver;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.objdb.ObjectStoreFactory;
import org.elkoserver.objdb.store.ObjectStore;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Main state data structure in a Repository.
 */
class Repository {
    /** Table for mapping object references in messages. */
    private RefTable myRefTable;

    /** Server object. */
    private Server myServer;

    /** Local object storage module. */
    private ObjectStore myObjectStore;

    /** Number of repository clients currently connected. */
    private int myRepClientCount;

    /** Flag that is set once server shutdown begins. */
    private boolean amShuttingDown;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Repository(Server server, Trace appTrace, TraceFactory traceFactory) {
        myServer = server;

        myObjectStore = ObjectStoreFactory.createAndInitializeObjectStore(server.props(), "conf.rep", appTrace);

        myRefTable = new RefTable(AlwaysBaseTypeResolver.theAlwaysBaseTypeResolver, traceFactory);
        myRefTable.addRef(new RepHandler(this, traceFactory));
        myRefTable.addRef(new AdminHandler(this, traceFactory));

        amShuttingDown = false;
        myRepClientCount = 0;
        server.registerShutdownWatcher(() -> {
            amShuttingDown = true;
            countRepClients(0);
        });
    }

    /**
     * Add to or subtract from the number of repository clients connected.  If
     * the number drops to zero and this server is in the midst of shutting
     * down, terminate the object store.
     *
     * @param delta  The amount to change the count by.
     */
    void countRepClients(int delta) {
        myRepClientCount += delta;
        if (amShuttingDown && myRepClientCount <= 0) {
            myObjectStore.shutdown();
        }
    }

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    boolean isShuttingDown() {
        return amShuttingDown;
    }

    /**
     * Get the object store currently in use.
     *
     * @return the object storage object for this server.
     */
    ObjectStore objectStore() {
        return myObjectStore;
    }

    /**
     * Get the ref table.
     *
     * @return the object ref table that resolves object reference strings for
     *    messages sent to this server.
     */
    RefTable refTable() {
        return myRefTable;
    }

    /**
     * Reinitialize the server.
     */
    void reinit() {
        myServer.reinit();
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    void shutdown(boolean kill) {
        myServer.shutdown(kill);
    }
}
