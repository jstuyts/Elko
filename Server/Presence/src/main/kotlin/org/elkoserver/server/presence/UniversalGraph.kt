package org.elkoserver.server.presence

import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel
import java.security.SecureRandom

/**
 * Social graph based on the notion that everybody is connected to everybody
 * else.  So we don't need to actually store anything, but can dynamically
 * iterate a user's friends: a user's friends who are online at any given
 * moment are all users who are online.
 *
 * In order to throttle the massive numbers of presence updates that will be
 * generated when lots of users are online at once, this graph also supports a
 * stochastic filter that randomly excludes users according to a tunable
 * parameter.
 */
internal class UniversalGraph : SocialGraph {
    /** The presence server lording over us.  */
    private lateinit var myMaster: PresenceServer

    /** The domain this graph describes.  */
    private lateinit var myDomain: Domain

    /** Number of pseudo-friends someone has, for throttling.  A negative
     * value is unthrottled. */
    private var myPseudoFriendCount = 0
    override fun init(master: PresenceServer, gorgel: Gorgel, domain: Domain, conf: JsonObject) {
        myMaster = master
        myDomain = domain
        myPseudoFriendCount = conf.getInt("friends", -1)
        gorgel.i?.run { info("init UniversalGraph for domain '${domain.name}'") }
    }

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    override fun domain() = myDomain

    private class UserFilter : FilteringIterator.Filter<ActiveUser, String> {
        override fun transform(from: ActiveUser) = from.ref
    }

    private inner class PseudoFriendFilter<V> internal constructor(base: Iterator<V>, private val myExclusion: V) : ExcludingIterator<V>(base) {
        private var myStochasticFriendshipOdds = 0f
        public override fun isExcluded(element: V) = when {
            element == myExclusion -> true
            myStochasticFriendshipOdds >= 1.0f -> false
            else -> theRandom.nextFloat() < myStochasticFriendshipOdds
        }

        init {
            myStochasticFriendshipOdds = if (myPseudoFriendCount < 0) {
                1.0f
            } else {
                val userCount = myMaster.userCount()
                if (userCount < myPseudoFriendCount) {
                    1.0f
                } else {
                    myPseudoFriendCount.toFloat() / userCount.toFloat()
                }
            }
        }
    }

    /**
     * Fetch the social graph for a new user presence from the object store.
     *
     * @param user  The user whose social graph should be fetched.
     */
    override fun loadUserGraph(user: ActiveUser) {
        val friends = Iterable {
            PseudoFriendFilter(FilteringIterator(myMaster.users().iterator(), UserFilter()), user.ref)
        }
        user.userGraphIsReady(friends, myDomain, myMaster)
    }

    override fun shutdown() {}
    override fun update(master: PresenceServer, domain: Domain, conf: JsonObject) {}

    companion object {
        /** Random number generator, for random friendship.  */
        @Deprecated("Global variable")
        private val theRandom = SecureRandom()
    }
}
