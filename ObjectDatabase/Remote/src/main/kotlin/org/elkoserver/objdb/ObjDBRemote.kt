package org.elkoserver.objdb

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.MessageDispatcher
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.net.ConnectionRetrier
import org.elkoserver.foundation.net.MessageHandlerFactory
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.Encodable
import org.elkoserver.json.JsonObject
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.objdb.store.ResultDesc
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.util.HashMap
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Asynchronous access to a remote instance of the object database.  This is
 * implemented as a connection to an external repository.
 *
 * Create an object to access a remote object repository.
 *
 * <p>The repository to connect to is specified by configuration
 * properties, but may be indicated in one of two different ways:
 *
 * <p>The property <code>"<i>propRoot</i>.service"</code>, if given, indicates
 * a repository name to ask the Broker for.  Specifying this property as
 * the special value 'any' indicates that any repository that the Broker
 * knows about will be acceptable.
 *
 * <p>Alternatively, a repository host may be specified directly using the
 * <code>"<i>propRoot</i>.host"</code> property.
 *
 * <p>However the repository is indicated, the following properties are
 * also recognized:
 *
 * <p>The property <code>"<i>propRoot</i>.retry"</code> may specify a retry
 * interval (in seconds), at which successive attempts will be made to
 * connect to the external repository if earlier attempts have failed.  The
 * value -1 (which is the default if this property is left unspecified)
 * indicates that no retries should be attempted.
 *
 * <p>The property <code>"<i>propRoot</i>.classdesc"</code> may specify a
 * (comma-separated) list of references to class description objects to
 * read from the repository at startup time.
 *  @param serviceFinder  Access to broker, to locate repository server.
 * @param myNetworkManager  Network manager, for making outbound connections.
 * @param localName  Name of this server.
 * @param props  Properties that the hosting server was configured with
 * @param propRoot  Prefix string for generating relevant configuration
 *    property names.
 */
class ObjDBRemote(serviceFinder: ServiceFinder,
                  private val myNetworkManager: NetworkManager,
                  localName: String,
                  props: ElkoProperties,
                  propRoot: String,
                  gorgel: Gorgel,
                  private val connectionRetrierWithoutLabelGorgel: Gorgel,
                  private val odbActorGorgel: Gorgel,
                  private val traceFactory: TraceFactory,
                  private val inputGorgel: Gorgel,
                  private val timer: Timer,
                  hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
                  jsonToObjectDeserializer: JsonToObjectDeserializer,
                  private val getRequestFactory: GetRequestFactory,
                  private val putRequestFactory: PutRequestFactory,
                  private val updateRequestFactory: UpdateRequestFactory,
                  private val queryRequestFactory: QueryRequestFactory,
                  private val removeRequestFactory: RemoveRequestFactory,
                  private val mustSendDebugReplies: Boolean) : ObjDBBase(gorgel, jsonToObjectDeserializer) {
    /** Connection to the repository, if there is one.  */
    private var myODBActor: ODBActor? = null

    /** Repository requests that have been issued to this object database, the
     * responses to which are still pending, either because the repository has
     * not yet responded or because it is not yet connected and the requests
     * haven't yet been transmitted.  Maps query tags to PendingRequest
     * objects.  */
    private val myPendingRequests: MutableMap<String, PendingRequest> = HashMap()

    /** Repository requests that haven't been transmitted due to an unconnected
     * repository, in temporal order.  */
    private var myUnsentRequests: MutableList<PendingRequest>? = null

    /** Contact information for the remote repository.  */
    private var myRepHost: HostDesc? = null

    /** Message dispatcher for repository connections.  */
    private val myDispatcher = MessageDispatcher(this, traceFactory, jsonToObjectDeserializer).apply {
        addClass(ODBActor::class.java)
    }

    /** Repository connection retry interval, in seconds, or -1 to take the
     * default.  */
    private val myRetryInterval: Int

    /** Flag to prevent reopening repository connection while shutting down.  */
    private var amClosing = false

    /** Message handler factory for repository connections.  */
    private val myMessageHandlerFactory = object : MessageHandlerFactory {
        override fun provideMessageHandler(connection: Connection?) =
                ODBActor(connection!!, this@ObjDBRemote, localName, myRepHost!!, myDispatcher, odbActorGorgel, mustSendDebugReplies)
    }

    private inner class RepositoryFoundHandler : Consumer<Array<ServiceDesc>> {
        override fun accept(obj: Array<ServiceDesc>) {
            if (obj[0].failure != null) {
                gorgel.error("unable to find repository: ${obj[0].failure}")
            } else {
                myRepHost = obj[0].asHostDesc(myRetryInterval)
                connectToRepository()
            }
        }
    }

    /**
     * Start attempting to connect to the repository if the property settings
     * said to do so.
     */
    private fun connectToRepository() {
        if (!amClosing) {
            val currentRepHost = myRepHost
            if (currentRepHost != null) {
                ConnectionRetrier(currentRepHost,
                        "repository",
                        myNetworkManager,
                        myMessageHandlerFactory,
                        timer,
                        connectionRetrierWithoutLabelGorgel.withAdditionalStaticTags(Tag("label", "repository")),
                        traceFactory.comm,
                        inputGorgel,
                        mustSendDebugReplies)
            }
        }
    }

    /**
     * Set the connection to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository;
     * this may be null, indicating that a connection has been lost.
     */
    fun repositoryConnected(odbActor: ODBActor?) {
        myODBActor = odbActor
        if (odbActor == null) {
            connectToRepository()
        } else {
            val unsentRequests = myUnsentRequests!!
            myUnsentRequests = null
            for (req in unsentRequests) {
                req.sendRequest(odbActor)
            }
        }
    }

    /**
     * Fetch an object from the repository.
     *
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     * the configured default.
     * @param handler  Handler to be called with the result.  The result will
     * be the object requested, or null if the object could not be
     * retrieved.
     */
    override fun getObject(ref: String, collectionName: String?, handler: Consumer<Any?>) {
        newRequest(getRequestFactory.create(ref, collectionName, handler))
    }

    /**
     * Handle a reply from the repository to a 'get' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    fun handleGetResult(tag: String?, results: Array<ObjectDesc>?) {
        handleRetrievalResult(tag, results)
    }

    /**
     * Handle a reply from the repository to a 'put' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    fun handlePutResult(tag: String?, results: Array<ResultDesc>?) {
        val req = myPendingRequests.remove(tag)
        if (req != null && results != null) {
            req.handleReply(results[0].failure)
        }
    }

    /**
     * Handle a reply from the repository to an 'update' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    fun handleUpdateResult(tag: String?, results: Array<ResultDesc>?) {
        val req = myPendingRequests.remove(tag)
        if (req != null && results != null) {
            req.handleReply(results[0].failure)
        }
    }

    /**
     * Handle a reply from the repository to a 'query' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    fun handleQueryResult(tag: String?, results: Array<ObjectDesc>?) {
        handleRetrievalResult(tag, results)
    }

    private fun handleRetrievalResult(tag: String?, results: Array<ObjectDesc>?) {
        val req = myPendingRequests.remove(tag)
        if (req != null && results != null) {
            val obj: Any?
            val failure = results[0].failure
            /* XXX this is just wrong. (As previously documented for queries. But why not for gets?) */
            obj = if (failure == null) {
                decodeObject(req.ref, results)
            } else {
                gorgel.error("repository error getting ${req.ref}: $failure")
                null
            }
            req.handleReply(obj)
        }
    }

    /**
     * Handle a reply from the repository to a 'remove' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    fun handleRemoveResult(tag: String?, results: Array<ResultDesc>?) {
        val req = myPendingRequests.remove(tag)
        if (req != null && results != null) {
            req.handleReply(results[0].failure)
        }
    }

    /**
     * Record a new request in the pending requests table.  If currently
     * connected to the repository, also send the request to it.
     *
     * @param req  The new request.
     */
    private fun newRequest(req: PendingRequest) {
        myPendingRequests[req.tag] = req
        val currentOdbActor = myODBActor
        if (currentOdbActor != null) {
            req.sendRequest(currentOdbActor)
        } else {
            val currentUnsentRequests = myUnsentRequests
            val actualUnsentRequests = if (currentUnsentRequests == null) {
                val newUnsentRequests = LinkedList<PendingRequest>()
                myUnsentRequests = newUnsentRequests
                newUnsentRequests
            } else {
                currentUnsentRequests
            }
            actualUnsentRequests.add(req)
        }
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     * the configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun putObject(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?) {
        newRequest(putRequestFactory.create(ref, obj, collectionName, requireNew, handler))
    }

    /**
     * Update an object in the repository.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be updated.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     * the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun updateObject(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?) {
        newRequest(updateRequestFactory.create(ref, version, obj, collectionName, handler))
    }

    /**
     * Query one or more objects from the object database.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     * configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     * be an array of the object(s) requested, or null if no objects could
     * be retrieved.
     */
    override fun queryObjects(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>) {
        newRequest(queryRequestFactory.create(template, collectionName, maxResults, handler))
    }

    /**
     * Delete an object from the repository.  It is not considered an error to
     * attempt to remove an object that is not there; such an operation always
     * succeeds.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to delete from, or null to
     * take the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    override fun removeObject(ref: String, collectionName: String?, handler: Consumer<Any?>?) {
        newRequest(removeRequestFactory.create(ref, collectionName, handler))
    }

    /**
     * Shutdown the object database.
     */
    override fun shutdown() {
        amClosing = true
        myODBActor?.close()
    }

    init {
        addClass("obji", ObjectDesc::class.java)
        addClass("stati", ResultDesc::class.java)
        loadClassDesc(props.getProperty("$propRoot.classdesc"))
        val odbPropRoot = "$propRoot.repository"
        myRetryInterval = props.intProperty("$odbPropRoot.retry", -1)
        var serviceName = props.getProperty("$odbPropRoot.service")
        if (serviceName != null) {
            myRepHost = null
            serviceName = if (serviceName == "any") "repository-rep" else "repository-rep-$serviceName"
            serviceFinder.findService(serviceName, RepositoryFoundHandler(), false)
        } else {
            myRepHost = hostDescFromPropertiesFactory.fromProperties(odbPropRoot)
            connectToRepository()
        }
    }
}
