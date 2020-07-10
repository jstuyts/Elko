package org.elkoserver.server.repository

import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.objdb.store.GetResultHandler
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.PutDesc
import org.elkoserver.objdb.store.QueryDesc
import org.elkoserver.objdb.store.RequestDesc
import org.elkoserver.objdb.store.RequestResultHandler
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.objdb.store.UpdateDesc
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Singleton handler for the repository 'rep' protocol.
 *
 * The 'rep' protocol consists of these requests:
 *
 * 'get' - Requests the retrieval of an object (and, optionally, its
 * contents) from the object store.
 *
 * 'put' - Requests the writing of an object into the object store.
 *
 * 'remove' - Requests the deletion of an object from the object store.
 */
internal class RepHandler(repository: Repository, commGorgel: Gorgel) : BasicProtocolHandler(commGorgel) {
    private val myObjectStore = repository.myObjectStore

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'rep'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "rep"

    /**
     * Handle the 'get' verb.
     *
     * Request data retrieval.
     *
     * @param from  The connection asking for the objects.
     * @param tag  Client tag for matching replies.
     * @param what  Objects requested.
     */
    @JsonMethod("tag", "what")
    operator fun get(from: RepositoryActor, tag: OptString,
                     what: Array<RequestDesc>) {
        myObjectStore.getObjects(what, object : GetResultHandler {
            override fun handle(results: Array<ObjectDesc>?) {
                from.send(msgGet(this@RepHandler, tag.value<String?>(null), results))
            }
        })
    }

    /**
     * Handle the 'put' verb.
     *
     * Request data be saved in persistent storage.
     *
     * @param from  The connection asking for the write.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be written.
     */
    @JsonMethod("tag", "what")
    fun put(from: RepositoryActor, tag: OptString,
            what: Array<PutDesc>) {
        myObjectStore.putObjects(what, object : RequestResultHandler {
            override fun handle(results: Array<out ResultDesc>) {
                from.send(msgPut(this@RepHandler, tag.value<String?>(null), results))
            }
        })
    }

    /**
     * Handle the 'update' verb.
     *
     * Request data be saved in persistent storage if it hasn't changed.
     *
     * @param from  The connection asking for the write.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be written.
     */
    @JsonMethod("tag", "what")
    fun update(from: RepositoryActor, tag: OptString,
               what: Array<UpdateDesc>) {
        myObjectStore.updateObjects(what, object : RequestResultHandler {
            override fun handle(results: Array<out ResultDesc>) {
                from.send(msgUpdate(this@RepHandler, tag.value<String?>(null), results))
            }
        })
    }

    /**
     * Handle the 'query' verb.
     *
     * Query the database for one or more objects.
     *
     * @param from  The connection asking for the objects.
     * @param tag  Client tag for matching replies.
     * @param what  Query templates for the objects requested.
     */
    @JsonMethod("tag", "what")
    fun query(from: RepositoryActor, tag: OptString,
              what: Array<QueryDesc>) {
        myObjectStore.queryObjects(what, object : GetResultHandler {
            override fun handle(results: Array<ObjectDesc>?) {
                from.send(msgQuery(this@RepHandler, tag.value<String?>(null), results))
            }
        })
    }

    /**
     * Handle the 'remove' verb.
     *
     * Request objects be deleted from storage.
     *
     * @param from  The connection asking for the deletions.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be deleted.
     */
    @JsonMethod("tag", "what")
    fun remove(from: RepositoryActor, tag: OptString,
               what: Array<RequestDesc>) {
        myObjectStore.removeObjects(what, object : RequestResultHandler {
            override fun handle(results: Array<out ResultDesc>) {
                from.send(msgRemove(this@RepHandler, tag.value<String?>(null), results))
            }
        })
    }
}
