package org.elkoserver.objdb

import org.elkoserver.foundation.actor.NonRoutingActor
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.util.trace.TraceFactory

/**
 * Actor representing a connection to a repository.
 *
 * @param connection  The connection for actually communicating to the
 *    repository.
 * @param myODB  Local interface to the remote repository.
 * @param localName  Name of this server.
 * @param host  Description of repository host address.
 * @param dispatcher  Message dispatcher for repository actors.
 */
class ODBActor(connection: Connection, private val myODB: ObjDBRemote, localName: String?,
                        host: HostDesc, dispatcher: MessageDispatcher, traceFactory: TraceFactory) : NonRoutingActor(connection, dispatcher, traceFactory) {

    /**
     * Handle loss of connection from the repository.
     *
     * @param connection  The repository connection that died.
     * @param reason  Exception explaining why.
     */
    override fun connectionDied(connection: Connection, reason: Throwable) {
        traceFactory.comm.eventm("lost repository connection $connection: $reason")
        myODB.repositoryConnected(null)
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
    @JSONMethod("tag", "results")
    operator fun get(from: ODBActor, tag: OptString, results: Array<ObjectDesc>) {
        myODB.handleGetResult(tag.value<String?>(null)!!, results)
    }

    /**
     * Handle the 'put' verb.
     *
     * Process the reply to an earlier 'put' request.
     */
    @JSONMethod("tag", "results")
    fun put(from: ODBActor, tag: OptString, results: Array<ResultDesc>) {
        myODB.handlePutResult(tag.value<String?>(null)!!, results)
    }

    /**
     * Handle the 'update' verb.
     *
     * Process the reply to an earlier 'update' request.
     */
    @JSONMethod("tag", "results")
    fun update(from: ODBActor, tag: OptString, results: Array<ResultDesc>) {
        myODB.handleUpdateResult(tag.value<String?>(null)!!, results)
    }

    /**
     * Handle the 'query' verb.
     *
     * Process the reply to an earlier 'query' request.
     */
    @JSONMethod("tag", "results")
    fun query(from: ODBActor, tag: OptString, results: Array<ObjectDesc>) {
        myODB.handleQueryResult(tag.value<String?>(null)!!, results)
    }

    /**
     * Handle the 'remove' verb.
     *
     * Process the reply to an earlier 'remove' request.
     */
    @JSONMethod("tag", "results")
    fun remove(from: ODBActor, tag: OptString, results: Array<ResultDesc>) {
        myODB.handleRemoveResult(tag.value<String?>(null)!!, results)
    }

    init {
        send(msgAuth(this, host.auth(), localName))
        myODB.repositoryConnected(this)
    }
}
