package org.elkoserver.server.presence

import org.elkoserver.json.JsonObject
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import java.util.function.Consumer

internal class SimpleSocialGraph : SocialGraph {
    /** Database that social graph is stored in.  */
    private lateinit var myODB: ObjDB

    /** The presence server lording over us.  */
    private lateinit var myMaster: PresenceServer

    /** The domain this graph describes.  */
    private lateinit var myDomain: Domain

    /** Trace object for diagnostics.  */
    private lateinit var tr: Trace

    /** Prefix string for looking up user graph records in the database.  */
    private lateinit var myPrefix: String

    override fun init(master: PresenceServer, domain: Domain, conf: JsonObject) {
        myODB = master.objDB()
        myODB.addClass("ugraf", UserGraphDesc::class.java)
        myMaster = master
        myDomain = domain
        tr = master.appTrace()
        myPrefix = conf.getString("prefix", "g")
        tr.worldi("init SimpleSocialGraph for domain '${domain.name()}', odb prefix '$myPrefix-'")
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
        myODB.getObject("$myPrefix-${user.ref()}", null, Consumer<Any?> { obj ->
            if (obj != null) {
                val desc = obj as UserGraphDesc
                val friends = Iterable { ArrayIterator(desc.friends) }
                user.userGraphIsReady(friends, myDomain, myMaster)
            } else {
                user.userGraphIsReady(null, myDomain, myMaster)
                tr.warningi("no social graph info for user ${user.ref()} in domain ${myDomain.name()}")
            }
        })
    }

    override fun shutdown() {}
    override fun update(master: PresenceServer, domain: Domain, conf: JsonObject) {}
}
