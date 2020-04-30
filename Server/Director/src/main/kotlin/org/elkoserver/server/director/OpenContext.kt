package org.elkoserver.server.director

import org.elkoserver.server.director.Director.Companion.isUserClone
import org.elkoserver.server.director.Director.Companion.userCloneSetName
import org.elkoserver.util.HashSetMulti
import java.util.Collections
import java.util.HashSet
import java.util.Objects

/**
 * Information describing an open context.
 *
 * @param myProvider  Who is providing the context.
 * @param myName  Context name.
 * @param isMine  true if the context was opened by request of this director.
 * @param myMaxCapacity  The maximum user capacity for the context.
 * @param myBaseCapacity  The base capacity for the (clone) context.
 * @param isRestricted  true if the context is entry restricted
 */
internal class OpenContext(private val myProvider: Provider, private val myName: String,
                           val isMine: Boolean, private val myMaxCapacity: Int, private val myBaseCapacity: Int,
                           val isRestricted: Boolean) {

    /** Context clone set name, if a clone (null if not).  */
    private var myCloneSetName: String?

    /** True if this context is a clone.  */
    var isClone: Boolean

    /** Users in this context.  */
    private val myUsers: MutableSet<String> = HashSet()

    /** User clones in this context.  */
    private val myUserClones = HashSetMulti<String>()

    /** Reason this context is closed to user entry, or null if it is not.  */
    private var myGateClosedReason: String? = null

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
     * Get this context's clone set name.
     *
     * @return the name of this context's clone set.
     */
    fun cloneSetName() = myCloneSetName

    /**
     * Close this context's gate, blocking new users from entering.
     *
     * @param reason  String describing why this is being done.
     */
    fun closeGate(reason: String) {
        myGateClosedReason = Objects.requireNonNullElse(reason, "context closed to new entries")
    }

    /**
     * Obtain a string describing the reason this context's gate is closed.
     *
     * @return a reason string for this context's gate closure, or null if the
     * gate is open.
     */
    fun gateClosedReason() = myGateClosedReason

    /**
     * Test if this context's gate is closed.  If the gate is closed, new users
     * may not enter, even if the context is not full.
     *
     * @return true iff this context's gate is closed.
     */
    fun gateIsClosed() = myGateClosedReason != null

    /**
     * Test if this context has reached its maximum capacity.
     *
     * @return true if this context cannot accept any more members.
     */
    val isFull: Boolean
        get() = if (myMaxCapacity < 0) { false } else { myUsers.size >= myMaxCapacity }

    /**
     * Test if this context has reached or exceeded its base capacity.
     *
     * @return true if this context is at or above its base capacity.
     */
    val isFullClone: Boolean
        get() = if (myBaseCapacity < 0) { false } else { myUsers.size >= myBaseCapacity }

    /**
     * Test if a given user is in this context.
     *
     * @param user  The name of the user to test for.
     */
    fun hasUser(user: String) = myUsers.contains(user) || myUserClones.contains(user)

    /**
     * Get the name of this context.
     *
     * @return this context's name.
     */
    fun name() = myName

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    fun openGate() {
        myGateClosedReason = null
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
        val thisKey = myProvider.dupKey()
        val otherKey = other.myProvider.dupKey()
        return if (thisKey!! < otherKey!!) this else other
    }

    /**
     * Get the provider for this context.
     *
     * @return the provider that is running this context.
     */
    fun provider() = myProvider

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
    fun users() = Collections.unmodifiableSet(myUsers)

    init {
        var dashPos = 0
        var dashCount = 0
        while (dashPos >= 0) {
            dashPos = myName.indexOf('-', dashPos)
            if (dashPos >= 0) {
                ++dashCount
                ++dashPos
            }
        }
        if (dashCount > 1) {
            isClone = true
            dashPos = myName.lastIndexOf('-')
            myCloneSetName = myName.substring(0, dashPos)
        } else {
            isClone = false
            myCloneSetName = null
        }
    }
}
