package org.elkoserver.server.presence

import com.grack.nanojson.JsonObject

/**
 * The client facet of a presence server actor.  This object represents the
 * state and functionality required when a connected entity is engaging in the
 * client protocol.
 *
 * @param myPresenceServer  The presence server whose client this is.
 * @param myActor  The actor associated with the client.
 */
internal class Client(private val myPresenceServer: PresenceServer, private val myActor: PresenceActor) {

    /**
     * Clean up when the client actor disconnects.
     */
    fun doDisconnect() {}

    /**
     * Take note of user metadata.
     *
     * @param userRef  The user to whom the metadata applies
     * @param userMeta  The user metadata itself.
     */
    fun noteUserMetadata(userRef: String, userMeta: JsonObject?) {
        myPresenceServer.noteUserMetadata(userRef, userMeta)
    }

    /**
     * Take note of context metadata.
     *
     * @param contextRef  The context to which the metadata applies
     * @param contextMeta  The context metadata itself.
     */
    fun noteContextMetadata(contextRef: String, contextMeta: JsonObject) {
        myPresenceServer.noteContextMetadata(contextRef, contextMeta)
    }

    /**
     * Take note that a user has entered one of this client's contexts.
     *
     * @param userName  The name of the user who entered.
     * @param contextName  The name of the context they entered.
     */
    fun noteUserEntry(userName: String, contextName: String) {
        myPresenceServer.addUserPresence(userName, contextName)
    }

    /**
     * Take note that a user has exited one of this client's contexts.
     *
     * @param userName  The name of the user who exited.
     * @param contextName  The name of the context they exited.
     */
    fun noteUserExit(userName: String, contextName: String) {
        myPresenceServer.removeUserPresence(userName, contextName, myActor)
    }

    /**
     * Take note that a context's user's presence info is not visible.
     *
     * @param contextName  The name of the context
     */
    fun noteInvisibleContext(contextName: String) {
        myPresenceServer.removeVisibleContext(contextName)
    }

    /**
     * Take note that a context's user's presence info is visible.
     *
     * @param contextName  The name of the context
     */
    fun noteVisibleContext(contextName: String) {
        myPresenceServer.addVisibleContext(contextName, myActor)
    }

    /**
     * Take note that a context is interested in presence updates for some
     * domain.
     *
     * @param contextName  The name of the context that is interested
     * @param domain   The domain of interest
     */
    fun subscribeToUpdates(contextName: String, domain: String) {
        myPresenceServer.addSubscriber(contextName, domain, myActor)
    }

    /**
     * Take note that a context is no longer interested in presence updates.
     *
     * @param contextName  The name of the context that has lost interest
     */
    fun unsubscribeToUpdates(contextName: String) {
        myPresenceServer.removeSubscriber(contextName)
    }
}
