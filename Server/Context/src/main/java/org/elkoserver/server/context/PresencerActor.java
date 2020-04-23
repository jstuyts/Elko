package org.elkoserver.server.context;

import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JsonObject;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Actor representing a connection to a director.
 */
class PresencerActor extends NonRoutingActor {
    /** Send group containing all the director connections. */
    private PresencerGroup myGroup;

    /**
     * Constructor.
     *
     * @param connection  The connection for actually communicating.
     * @param dispatcher  Message dispatcher for incoming messages.
     * @param group  The send group for all the directors.
     * @param host  Host description for this connection.
     */
    PresencerActor(Connection connection, MessageDispatcher dispatcher,
                   PresencerGroup group, HostDesc host, TraceFactory traceFactory) {
        super(connection, dispatcher, traceFactory);
        myGroup = group;
        group.admitMember(this);
        send(msgAuth(this, host.auth(), group.contextor().serverName()));
    }

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        traceFactory.comm.eventm("lost director connection " + connection + ": " +
                          reason);
        myGroup.expelMember(this);
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "presence";
    }

    /**
     * Handle the 'gtou' verb.
     *
     * Process a notification about a group of users to a particular user
     *
     * @param userRef  The user who may be interested in this
     * @param contextRef  The context the user is in
     * @param group  Info about the other users whose presences are online
     */
    @JSONMethod({ "touser", "ctx", "group" })
    public void gtou(PresencerActor from, String userRef, String contextRef,
                     GToUDomainInfo[] group) {
        for (GToUDomainInfo info : group) {
            for (GToUFriendInfo friend : info.friends) {
                myGroup.contextor().observePresenceChange(
                    contextRef, userRef, info.domain, friend.user,
                    friend.userMeta, friend.context, friend.contextMeta, true);
            }
        }
    }

    private static class GToUDomainInfo {
        final String domain;
        final GToUFriendInfo[] friends;
        @JSONMethod({ "domain", "friends" })
            GToUDomainInfo(String domain, GToUFriendInfo[] friends) {
            this.domain = domain;
            this.friends = friends;
        }
    }

    private static class GToUFriendInfo {
        final String user;
        final JsonObject userMeta;
        final String context;
        final JsonObject contextMeta;
        @JSONMethod({ "user", "?umeta", "ctx", "?cmeta" })
        GToUFriendInfo(String user, JsonObject userMeta, String context,
                       JsonObject contextMeta)
        {
            this.user = user;
            this.userMeta = userMeta;
            this.context = context;
            this.contextMeta = contextMeta;
        }
    }

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
    @JSONMethod({ "user", "?umeta", "ctx", "?cmeta", "on", "togroup" })
    public void utog(PresencerActor from, String userRef, JsonObject userMeta,
                     String contextRef, JsonObject contextMeta, boolean on,
                     UToGDomainInfo[] toGroup) {
        for (UToGDomainInfo domainInfo : toGroup) {
            for (UToGContextInfo contextInfo : domainInfo.who) {
                for (String friend : contextInfo.users) {
                    myGroup.contextor().observePresenceChange(
                        contextInfo.context, friend, domainInfo.domain,
                        userRef, userMeta, contextRef, contextMeta, on);
                }
            }
        }
    }

    private static class UToGDomainInfo {
        final String domain;
        final UToGContextInfo[] who;
        @JSONMethod({ "domain", "who" })
        UToGDomainInfo(String domain, UToGContextInfo[] who) {
            this.domain = domain;
            this.who = who;
        }
    }

    private static class UToGContextInfo {
        final String context;
        final String[] users;
        @JSONMethod({ "ctx", "users" })
        UToGContextInfo(String context, String[] users) {
            this.context = context;
            this.users = users;
        }
    }
}
