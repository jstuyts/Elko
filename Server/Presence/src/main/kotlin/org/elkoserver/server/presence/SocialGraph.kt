package org.elkoserver.server.presence

import com.grack.nanojson.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Interface representing an entire social graph, irrespective of its
 * underlying semantics or storage implementation.
 */
internal interface SocialGraph {
    /**
     * Initialize this social graph.
     *
     * @param master  The object representing the presence server environment
     * in which this graph will be used.
     * @param domain  The domain for which this graph is the applicable
     * description.
     * @param conf  A JsonObject full of configuration information, whose
     * particulars depend on the class implementing this interface.
     */
    fun init(master: PresenceServer, gorgel: Gorgel, domain: Domain, conf: JsonObject)

    /**
     * Fetch the social graph for a new user presence from persistent storage.
     * Implementors of this method must then invoke the user's
     * userGraphIsReady() method to inform the system that the graph
     * information can now be used.
     *
     * @param user  The user whose social graph should be fetched.
     */
    fun loadUserGraph(user: ActiveUser)

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    fun domain(): Domain

    /**
     * Update this social graph.
     *
     * @param master  The object representing the presence server environment
     * in which this graph is being used.
     * @param domain  The domain being updated.
     * @param conf  A JsonObject full of Domain-specific configuration update
     * information, whose particulars depend on the class implementing this
     * interface.
     */
    fun update(master: PresenceServer, domain: Domain, conf: JsonObject)

    /**
     * Do any work required prior to shutting down the server.  This method
     * will be called by the PresenceServer as part of its orderly shutdown
     * procedure.
     */
    fun shutDown()
}
