package org.elkoserver.server.presence

import org.elkoserver.json.JsonObject
import org.elkoserver.objdb.ObjDb
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

internal class SimpleSocialGraph : SocialGraph {
    /** Database that social graph is stored in.  */
    private lateinit var myObjDb: ObjDb

    /** The presence server lording over us.  */
    private lateinit var myMaster: PresenceServer

    /** The domain this graph describes.  */
    private lateinit var myDomain: Domain

    /** Trace object for diagnostics.  */
    private lateinit var myGorgel: Gorgel

    /** Prefix string for looking up user graph records in the database.  */
    private lateinit var myPrefix: String

    override fun init(master: PresenceServer, gorgel: Gorgel, domain: Domain, conf: JsonObject) {
        myObjDb = master.objDb
        myObjDb.addClass("ugraf", UserGraphDesc::class.java)
        myMaster = master
        myDomain = domain
        myGorgel = gorgel
        myPrefix = conf.getString("prefix", "g")
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
        myObjDb.getObject("$myPrefix-${user.ref}", null, Consumer { obj ->
            if (obj != null) {
                val desc = obj as UserGraphDesc
                val friends = Iterable { ArrayIterator(desc.friends) }
                user.userGraphIsReady(friends, myDomain, myMaster)
            } else {
                user.userGraphIsReady(null, myDomain, myMaster)
                myGorgel.warn("no social graph info for user ${user.ref} in domain ${myDomain.name}")
            }
        })
    }

    override fun shutdown() {}
    override fun update(master: PresenceServer, domain: Domain, conf: JsonObject) {}
}
