package org.elkoserver.server.presence

import com.grack.nanojson.JsonObject
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import java.util.LinkedList

/**
 * Information we are keeping track of with respect to an online user.  In
 * particular, we track the user's presences: all the contexts in which the
 * user is current present.
 *
 * @param ref  The ref associated with the client.
 */
internal class ActiveUser(internal val ref: String, private val domainRegistry: DomainRegistry) {

    /** Number of domains that are loaded.  */
    private var myDomainLoadCount = 0

    /** Current online presences of this user, stored in the form of an array
     * of the refs of the contexts in which they are present.  */
    internal var presences: Array<String?> = arrayOf()
        private set

    /** The members of the user's social graph, or null if not yet loaded, by
     * domain index.  Note: these are called 'friends' because the word is
     * short and the structural meaning is clear, but they might not actually
     * be friends per se (i.e., they could be rivals or enemies).  */
    private var myFriendsByDomain: ArrayList<Iterable<String>?>? = null

    /** Optional user metadata for this user.  */
    private var myMetadata: JsonObject? = null
    fun noteMetadata(metadata: JsonObject?) {
        myMetadata = metadata
    }

    /**
     * Add a new presence for this user to the collection we are following.
     *
     * @param context  The context the user is in
     * @param master  The presence server master instance.
     */
    fun addPresence(context: String, master: PresenceServer) {
        /* The collection of presences is kept in a simple array, rather than
           an indexed collection object such as a map, because a given active
           user is likely to have very few of these (i.e., in the vast, vast
           majority of cases, typically just one), meaning that locating
           entries by linear search of the array is quite reasonable and does
           not justify the additional storage overhead of a more complicated
           data structure. */
        if (presences.isEmpty()) {
            /* Start with a single element array, to hold the one presence. */
            presences = arrayOf(context)
        } else {
            /* If there are existing presences, scan the presences array for a
               null entry, so that we can reuse an existing slot in the array
               rather than reallocating and copying it. */
            /* A vacant slot! remember it in case we need one. */
            val nullIdx = presences.indices.firstOrNull { presences[it] == null }
                    ?: -1
            if (0 <= nullIdx) {
                /* If we found a vacant slot, put the new entry there. */
                presences[nullIdx] = context
            } else {
                /* Otherwise, enlarge the presences array by one and put the
                   new entry at the end. We expand by just one because, as with
                   the justification for using an array in the first place, the
                   size is unlikely to grow further. */
                presences = arrayOf(*presences, context)
            }
        }
        /* If the social graph is loaded, perform the notifications associated
           with a new presence's arrival.  If it's not loaded, these
           notifications will happen later, when the graph data arrives. */
        if (graphsAreReady()) {
            notifyFriendsAboutMe(true, context, master)
            notifyMeAboutFriends(context, master)
        }
    }

    /**
     * Create a representation of this user's social graph connections,
     * suitable for delivery in a server state dump.  This is intended for
     * debugging and testing; the format of the data object produced should
     * not be regarded as stable!
     *
     * @return a JSON literal array encoding this user's social graph.
     */
    fun encodeFriendsDump(): JsonLiteralArray {
        val result = JsonLiteralArray()
        if (graphsAreReady()) {
            myFriendsByDomain!!.indices
                    .map {
                        JsonLiteral().apply {
                            addParameter("domain", domainRegistry.domain(it).name)
                            addParameter("friends", myFriendsByDomain!![it])
                            finish()
                        }
                    }
                    .forEach(result::addElement)
        }
        result.finish()
        return result
    }

    /**
     * Test if the social graph data for this user have been loaded.
     *
     * @return true iff this user's social graph data are immediately
     * available.
     */
    private fun graphsAreReady(): Boolean {
        return myFriendsByDomain != null &&
                myDomainLoadCount == domainRegistry.maxIndex()
    }

    /**
     * Notify this user's friends about this user's change in presence.
     * Actually, we don't notify the friends directly; rather, we notify the
     * clients (i.e., context servers) the friends are on: one message to each
     * client with at least one such user.  Moreover, we only send a
     * notification for a given friend if the friend is in a context that has
     * subscribed to the domain of the friend's relationship to this user.
     *
     * @param on  true if the user came online, false if they went offline
     * @param userContext  The context that the user entered or exited
     * @param master  The presence server master instance.
     */
    private fun notifyFriendsAboutMe(on: Boolean, userContext: String?,
                                     master: PresenceServer) {
        /* For each client, a per domain collection:
           For each domain, a per context collection:
           For each context, a list of users */
        val tell: MutableMap<PresenceActor, MutableMap<Domain, MutableMap<String, MutableList<String?>>>> = HashMap()
        for (i in myFriendsByDomain!!.indices) {
            val friends = myFriendsByDomain!![i]
            if (friends != null) {
                val domain = domainRegistry.domain(i)
                friends
                        .mapNotNull(master::getActiveUser)
                        .forEach {
                            for (context in it.presences) {
                                if (context != null) {
                                    val client = domain.subscriber(context)
                                    if (client != null) {
                                        val dtell = tell.computeIfAbsent(client) { HashMap() }
                                        val ctell = dtell.computeIfAbsent(domain) { HashMap() }
                                        val nameList = ctell.computeIfAbsent(context) { LinkedList() }
                                        nameList.add(it.ref)
                                    }
                                }
                            }
                        }
            }
        }
        for ((key, value) in tell) {
            key.send(msgUserToGroup(ref, myMetadata, userContext,
                    on, value, master))
        }
    }

    /**
     * Notify the user's presence (that presence's context server, actually)
     * about the online presences of the other users in their social graph
     * who are online now.  Note that this notification only happens if the
     * user is in a context that has subscribed to one or more of the user's
     * friend domains.
     *
     * @param userContext  The context of my user presence which is to receive
     * the notification
     * @param master  The presence server master instance.
     */
    private fun notifyMeAboutFriends(userContext: String?, master: PresenceServer) {
        val friends: MutableMap<Domain, List<FriendInfo?>> = HashMap()
        var client: PresenceActor? = null
        for (i in 0 until domainRegistry.maxIndex()) {
            if (myFriendsByDomain!![i] != null) {
                val friendList: MutableList<FriendInfo?> = LinkedList()
                val domain = domainRegistry.domain(i)
                client = userContext?.let(domain::subscriber)
                if (client != null) {
                    for (friendName in myFriendsByDomain!![i]!!) {
                        val friend = master.getActiveUser(friendName)
                        friend?.presences?.filterNotNull()?.mapTo(friendList) {
                            FriendInfo(friendName,
                                    friend.myMetadata,
                                    it,
                                    master.getContextMetadata(it))
                        }
                    }
                    friends[domain] = friendList
                }
            }
        }
        if (friends.isNotEmpty() && client != null) {
            /* Only send me a message telling me about my friend's presences if
               I actually have (online) friends. */
            client.send(msgGroupToUser(ref, userContext, friends))
        }
    }

    /**
     * Obtain the number of online presences this user currently has.
     *
     * @return the count of presences for this user.
     */
    fun presenceCount() = presences.count { it != null }

    /**
     * Remove an existing presence for this user from the collection we are
     * following.
     *
     * @param context  The context the user was in
     * @param master  The presence server master instance.
     *
     * @return true if the presence was successfully removed, false if not
     */
    fun removePresence(context: String, master: PresenceServer): Boolean {
        for (i in presences.indices) {
            if (context == presences[i]) {
                presences[i] = null
                notifyFriendsAboutMe(false, context, master)
                return true
            }
        }
        return false
    }

    /**
     * Set this user's friends set for some domain, i.e., the collection of
     * other users in the domain's social graph.  Note that this should only
     * ever be called once per user per domain, and will have the side effect
     * of performing any notifications that were pending the loading of the
     * social graph.
     *
     * @param friends  This user's social graph connections, represented as
     * an object that encapsulates the domain's social graph's
     * implementation's representation of a single user's connections.
     * @param domain  Social graph domain these connections belong to.
     * @param master  The presence server master instance.
     */
    fun userGraphIsReady(friends: Iterable<String>?, domain: Domain,
                         master: PresenceServer) {
        if (myFriendsByDomain == null) {
            val count = domainRegistry.maxIndex()
            myFriendsByDomain = ArrayList(count)
            for (i in 0 until count) {
                myFriendsByDomain!!.add(null)
            }
        }
        if (myFriendsByDomain!![domain.index] != null) {
            throw RuntimeException("duplicate setFriends call for user $ref, domain ${domain.name}")
        }
        myFriendsByDomain!![domain.index] = friends
        ++myDomainLoadCount
        if (graphsAreReady()) {
            for (context in presences) {
                notifyFriendsAboutMe(true, context, master)
                notifyMeAboutFriends(context, master)
            }
        }
    }

    /**
     * Simple encodable struct class holding the presence information
     * describing an online member of a user's social graph: a pair consisting
     * of the friend's user ref and the context ref of the context they are in.
     */
    internal class FriendInfo internal constructor(private val myUser: String, private val myUserMeta: JsonObject?, private val myContext: String,
                                                   private val myContextMeta: JsonObject?) : Encodable {
        override fun encode(control: EncodeControl) =
                JsonLiteral(control).apply {
                    addParameter("user", myUser)
                    addParameterOpt("umeta", myUserMeta)
                    addParameter("ctx", myContext)
                    addParameterOpt("cmeta", myContextMeta)
                    finish()
                }

    }
}
