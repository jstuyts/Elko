package org.elkoserver.server.context

import org.elkoserver.foundation.actor.Actor
import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory

/**
 * Actor representing a connection to a director.
 *
 * @param connection  The connection for actually communicating.
 * @param dispatcher  Message dispatcher for incoming messages.
 * @param myGroup  The send group for all the directors.
 * @param host  Host description for this connection.
 */
internal class PresencerActor(connection: Connection?, dispatcher: MessageDispatcher?,
                              private val myGroup: PresencerGroup, host: HostDesc, traceFactory: TraceFactory?) : NonRoutingActor(connection, dispatcher, traceFactory) {

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        traceFactory.comm.eventm("lost director connection $connection: $reason")
        myGroup.expelMember(this)
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "presence"

    /**
     * Handle the 'gtou' verb.
     *
     * Process a notification about a group of users to a particular user
     *
     * @param userRef  The user who may be interested in this
     * @param contextRef  The context the user is in
     * @param group  Info about the other users whose presences are online
     */
    @JSONMethod("touser", "ctx", "group")
    fun gtou(from: PresencerActor, userRef: String, contextRef: String, group: Array<GToUDomainInfo>) {
        for (info in group) {
            for (friend in info.friends) {
                myGroup.contextor().observePresenceChange(
                        contextRef, userRef, info.domain, friend.user,
                        friend.userMeta, friend.context, friend.contextMeta, true)
            }
        }
    }

    internal class GToUDomainInfo @JSONMethod("domain", "friends") internal constructor(val domain: String, val friends: Array<GToUFriendInfo>)

    internal class GToUFriendInfo @JSONMethod("user", "?umeta", "ctx", "?cmeta") internal constructor(val user: String, val userMeta: JsonObject, val context: String,
                                                                                                     val contextMeta: JsonObject)

    /**
     * Handle the 'utog' verb.
     *
     * Process a notification about a user to a group of users
     *
     * @param userRef  The user about whom this notification concerns
     * @param userMeta  Optional user metadata
     * @param contextRef  The context that this user has entered or exited
     * @param contextMeta  Optional context metadata
     * @param on  Flag indicating whether the presence is coming online or not
     * @param toGroup  List of users who may be interested in this
     */
    @JSONMethod("user", "?umeta", "ctx", "?cmeta", "on", "togroup")
    fun utog(from: PresencerActor, userRef: String, userMeta: JsonObject?, contextRef: String, contextMeta: JsonObject?, on: Boolean, toGroup: Array<UToGDomainInfo>) {
        for (domainInfo in toGroup) {
            for (contextInfo in domainInfo.who) {
                for (friend in contextInfo.users) {
                    myGroup.contextor().observePresenceChange(
                            contextInfo.context, friend, domainInfo.domain,
                            userRef, userMeta, contextRef, contextMeta, on)
                }
            }
        }
    }

    internal class UToGDomainInfo @JSONMethod("domain", "who") internal constructor(val domain: String, val who: Array<UToGContextInfo>)

    internal class UToGContextInfo @JSONMethod("ctx", "users") internal constructor(val context: String, val users: Array<String>)

    init {
        myGroup.admitMember(this)
        send(Actor.msgAuth(this, host.auth(), myGroup.contextor().serverName()))
    }
}
