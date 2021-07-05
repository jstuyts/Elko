package org.elkoserver.objectdatabase.store

/**
 * Access to a persistent object data storage mechanism.
 *
 * This interface is used by both the Repository (which provides object
 * storage remotely via a JSON protocol) and [ ] (which provides object storage locally via
 * direct access to an object database).  In either case, they are configured
 * with the fully qualified class name of an implementor of this interface,
 * which they instantiate at startup time.
 */
interface ObjectStore {
    /**
     * Service a 'get' request.  This is a request to retrieve one or more
     * objects from the store.
     *
     * @param what  The objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     * or failure indicators), when available.
     */
    fun getObjects(what: Array<RequestDesc>, handler: GetResultHandler)

    /**
     * Service a 'query' request.  This is a request to query one or more
     * objects from the store.
     *
     * @param what  Query templates for the objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     * or failure indicators), when available.
     */
    fun queryObjects(what: Array<QueryDesc>, handler: GetResultHandler)

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    fun putObjects(what: Array<PutDesc>, handler: RequestResultHandler)

    /**
     * Service a 'remove' request.  This is a request to delete one or more
     * objects from the store.
     *
     * @param what  The objects to be removed.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    fun removeObjects(what: Array<RequestDesc>, handler: RequestResultHandler)

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    fun shutDown()

    /**
     * Service an 'update' request.  This is a request to write one or more
     * objects to the store, subject to a version number check to assure
     * atomicity.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     * failure indicators), when available.
     */
    fun updateObjects(what: Array<UpdateDesc>, handler: RequestResultHandler)
}
