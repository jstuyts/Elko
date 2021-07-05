@file:Suppress("unused")

package org.elkoserver.server.presence.simple

import com.grack.nanojson.JsonObject
import org.elkoserver.json.getOptionalString
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.server.presence.ActiveUser
import org.elkoserver.server.presence.Domain
import org.elkoserver.server.presence.PresenceServer
import org.elkoserver.server.presence.SocialGraph
import org.elkoserver.util.trace.slf4j.Gorgel

internal class SimpleSocialGraph : SocialGraph {
    /** Database that social graph is stored in.  */
    private lateinit var myObjectDatabase: ObjectDatabase

    /** The presence server lording over us.  */
    private lateinit var myMaster: PresenceServer

    /** The domain this graph describes.  */
    private lateinit var myDomain: Domain

    /** Trace object for diagnostics.  */
    private lateinit var myGorgel: Gorgel

    /** Prefix string for looking up user graph records in the database.  */
    private lateinit var myPrefix: String

    override fun init(master: PresenceServer, gorgel: Gorgel, domain: Domain, conf: JsonObject) {
        myObjectDatabase = master.objectDatabase
        @Suppress("SpellCheckingInspection")
        myObjectDatabase.addClass("ugraf", UserGraphDesc::class.java)
        myMaster = master
        myDomain = domain
        myGorgel = gorgel
        myPrefix = conf.getOptionalString("prefix", "g")
        myGorgel.i?.run { info("init SimpleSocialGraph for domain '${domain.name}', obj db prefix '$myPrefix-'") }
    }

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    override fun domain() = myDomain

    /**
     * Fetch the social graph for a new user presence from the object store.
     *
     * @param user  The user whose social graph should be fetched.
     */
    override fun loadUserGraph(user: ActiveUser) {
        myObjectDatabase.getObject("$myPrefix-${user.ref}") { obj ->
            if (obj != null) {
                val desc = obj as UserGraphDesc
                val friends = desc.friends.asIterable()
                user.userGraphIsReady(friends, myDomain, myMaster)
            } else {
                user.userGraphIsReady(null, myDomain, myMaster)
                myGorgel.warn("no social graph info for user ${user.ref} in domain ${myDomain.name}")
            }
        }
    }

    override fun shutDown() {}
    override fun update(master: PresenceServer, domain: Domain, conf: JsonObject) {}
}
