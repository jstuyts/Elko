package org.elkoserver.server.director

import org.elkoserver.util.HashSetMultiImpl

/**
 * Information describing an open context.
 *
 * @param provider  Who is providing the context.
 * @param name  Context name.
 * @param isMine  true if the context was opened by request of this director.
 * @param myMaxCapacity  The maximum user capacity for the context.
 * @param myBaseCapacity  The base capacity for the (clone) context.
 * @param isRestricted  true if the context is entry restricted
 */
internal class OpenContext(internal val provider: Provider, internal val name: String,
                           val isMine: Boolean, private val myMaxCapacity: Int, private val myBaseCapacity: Int,
                           val isRestricted: Boolean) {

    /** Context clone set name, if a clone (null if not).  */
    internal var cloneSetName: String?
        private set

    /** True if this context is a clone.  */
    var isClone: Boolean

    /** Users in this context.  */
    private val myUsers: MutableSet<String> = HashSet()

    /** User clones in this context.  */
    private val myUserClones = HashSetMultiImpl<String>()

    /** Reason this context is closed to user entry, or null if it is not.  */
    internal var gateClosedReason: String? = null
        private set

    /**
     * Add a user to this context.
     *
     * @param user  The name of the user to add.
     */
    fun addUser(user: String) {
        myUsers.add(user)
        if (isUserClone(user)) {
            myUserClones.add(userCloneSetName(user))
        }
    }

    /**
     * Close this context's gate, blocking new users from entering.
     *
     * @param reason  String describing why this is being done.
     */
    fun closeGate(reason: String?) {
        gateClosedReason = reason ?: "context closed to new entries"
    }

    /**
     * Test if this context's gate is closed.  If the gate is closed, new users
     * may not enter, even if the context is not full.
     *
     * @return true iff this context's gate is closed.
     */
    fun gateIsClosed() = gateClosedReason != null

    /**
     * Test if this context has reached its maximum capacity.
     *
     * @return true if this context cannot accept any more members.
     */
    val isFull: Boolean
        get() = if (myMaxCapacity < 0) {
            false
        } else {
            myUsers.size >= myMaxCapacity
        }

    /**
     * Test if this context has reached or exceeded its base capacity.
     *
     * @return true if this context is at or above its base capacity.
     */
    val isFullClone: Boolean
        get() = if (myBaseCapacity < 0) {
            false
        } else {
            myUsers.size >= myBaseCapacity
        }

    /**
     * Test if a given user is in this context.
     *
     * @param user  The name of the user to test for.
     */
    fun hasUser(user: String) = myUsers.contains(user) || myUserClones.contains(user)

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    fun openGate() {
        gateClosedReason = null
    }

    /**
     * Given a context that is a duplicate of this one, pick the one that
     * should be closed to eliminate the duplication.  The victim is the one
     * whose provider has the lexically lowest dupKey() value.
     *
     * @param other  The other context to compare against.
     *
     * @return the context (this or other) that should be closed.
     */
    fun pickDupToClose(other: OpenContext): OpenContext {
        val thisKey = provider.dupKey()
        val otherKey = other.provider.dupKey()
        return if (thisKey!! < otherKey!!) this else other
    }

    /**
     * Remove a user from this context.
     *
     * @param user  The name of the user to remove.
     */
    fun removeUser(user: String) {
        myUsers.remove(user)
        if (isUserClone(user)) {
            myUserClones.remove(userCloneSetName(user))
        }
    }

    /**
     * Get the number of users currently in this context.
     *
     * @return the number of users in the context described by this object.
     */
    fun userCount() = myUsers.size

    /**
     * Get a read-only view of the names of the users in this context.
     *
     * @return a set of this context's user names.
     */
    fun users(): Set<String> = myUsers

    init {
        var dashPos = 0
        var dashCount = 0
        while (dashPos >= 0) {
            dashPos = name.indexOf('-', dashPos)
            if (dashPos >= 0) {
                ++dashCount
                ++dashPos
            }
        }
        if (dashCount > 1) {
            isClone = true
            dashPos = name.lastIndexOf('-')
            cloneSetName = name.take(dashPos)
        } else {
            isClone = false
            cloneSetName = null
        }
    }
}
