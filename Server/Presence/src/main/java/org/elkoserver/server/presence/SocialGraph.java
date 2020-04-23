package org.elkoserver.server.presence;

import org.elkoserver.json.JsonObject;

/**
 * Interface representing an entire social graph, irrespective of its
 * underlying semantics or storage implementation.
 */
interface SocialGraph {
    /**
     * Initialize this social graph.
     *
     * @param master  The object representing the presence server environment
     *    in which this graph will be used.
     * @param domain  The domain for which this graph is the applicable
     *    description.
     * @param conf  A JsonObject full of configuration information, whose
     *    particulars depend on the class implementing this interface.
     */
    void init(PresenceServer master, Domain domain, JsonObject conf);

    /**
     * Fetch the social graph for a new user presence from persistent storage.
     * Implementors of this method must then invoke the user's
     * userGraphIsReady() method to inform the system that the graph
     * information can now be used.
     *
     * @param user  The user whose social graph should be fetched.
     */
    void loadUserGraph(ActiveUser user);

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    Domain domain();

    /**
     * Update this social graph.
     *
     * @param master  The object representing the presence server environment
     *    in which this graph is being used.
     * @param domain  The domain being updated.
     * @param conf  A JsonObject full of Domain-specific configuration update
     *    information, whose particulars depend on the class implementing this
     *    interface.
     */
    void update(PresenceServer master, Domain domain, JsonObject conf);

    /**
     * Do any work required prior to shutting down the server.  This method
     * will be called by the PresenceServer as part of its orderly shutdown
     * procedure.
     */
    void shutdown();
}
