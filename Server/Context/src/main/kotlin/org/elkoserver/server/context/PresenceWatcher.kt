package org.elkoserver.server.context

/**
 * Interface implemented by mods that wish to be notified when the presence
 * of other users in a user's social graph changes.
 *
 * To enable this notification, mods may implement this interface, though
 * only one mod per user and one mod per context may implement it.
 */
internal interface PresenceWatcher {
    /**
     * Take notice that a user elsewhere has come or gone.
     *
     * @param observerRef  Ref of user who cares about this
     * @param domain  Presence domain of relationship between users
     * @param whoRef  Ref of user who came or went
     * @param whereRef  Ref of context the entered or exited
     * @param on  True if they came, false if they left
     */
    fun notePresenceChange(observerRef: String?, domain: String?, whoRef: String?,
                           whereRef: String?, on: Boolean)
}