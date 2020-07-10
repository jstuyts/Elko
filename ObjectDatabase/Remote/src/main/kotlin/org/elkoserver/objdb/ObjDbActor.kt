package org.elkoserver.objdb

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Actor representing a connection to a repository.
 *
 * @param connection  The connection for actually communicating to the
 *    repository.
 * @param myObjDb  Local interface to the remote repository.
 * @param localName  Name of this server.
 * @param host  Description of repository host address.
 * @param dispatcher  Message dispatcher for repository actors.
 */
class ObjDbActor(
        connection: Connection,
        private val myObjDb: ObjDbRemote,
        localName: String?,
        host: HostDesc,
        dispatcher: MessageDispatcher,
        gorgel: Gorgel,
        mustSendDebugReplies: Boolean) : NonRoutingActor(connection, dispatcher, gorgel, mustSendDebugReplies) {

    /**
     * Handle loss of connection from the repository.
     *
     * @param connection  The repository connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        gorgel.i?.run { info("lost repository connection $connection: $reason") }
        myObjDb.repositoryConnected(null)
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'rep'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "rep"

    /**
     * Handle the 'get' verb.
     *
     * Process the reply to an earlier 'get' request.
     */
    @JsonMethod("tag", "results")
    operator fun get(from: ObjDbActor, tag: OptString, results: Array<ObjectDesc>) {
        myObjDb.handleGetResult(tag.value<String?>(null), results)
    }

    /**
     * Handle the 'put' verb.
     *
     * Process the reply to an earlier 'put' request.
     */
    @JsonMethod("tag", "results")
    fun put(from: ObjDbActor, tag: OptString, results: Array<ResultDesc>) {
        myObjDb.handlePutResult(tag.value<String?>(null), results)
    }

    /**
     * Handle the 'update' verb.
     *
     * Process the reply to an earlier 'update' request.
     */
    @JsonMethod("tag", "results")
    fun update(from: ObjDbActor, tag: OptString, results: Array<ResultDesc>) {
        myObjDb.handleUpdateResult(tag.value<String?>(null), results)
    }

    /**
     * Handle the 'query' verb.
     *
     * Process the reply to an earlier 'query' request.
     */
    @JsonMethod("tag", "results")
    fun query(from: ObjDbActor, tag: OptString, results: Array<ObjectDesc>) {
        myObjDb.handleQueryResult(tag.value<String?>(null), results)
    }

    /**
     * Handle the 'remove' verb.
     *
     * Process the reply to an earlier 'remove' request.
     */
    @JsonMethod("tag", "results")
    fun remove(from: ObjDbActor, tag: OptString, results: Array<ResultDesc>) {
        myObjDb.handleRemoveResult(tag.value<String?>(null), results)
    }

    init {
        send(msgAuth(this, host.auth, localName))
        myObjDb.repositoryConnected(this)
    }
}
