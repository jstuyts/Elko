package org.elkoserver.objdb

import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.JSONLiteralFactory.type
import org.elkoserver.json.JsonObject
import java.util.function.Consumer

/**
 * A pending request to the repository.
 *
 * @param myHandler  Handler to call on the request result.
 * @param myRef  Object reference being operated on.
 * @param myCollectionName  Name of collection to get from, or null to take
 *    the configured default.
 */
internal class PendingRequest private constructor(private val myHandler: Consumer<Any?>?, private val myRef: String, private val myCollectionName: String?) {
    /** Tag to match request with reply  */
    private val myTag: String

    /** Encoded request message.  */
    private var myMsg: JSONLiteral? = null

    /**
     * Handle a reply from the repository.
     *
     * @param obj  The reply object.
     */
    fun handleReply(obj: Any?) {
        myHandler?.accept(obj)
    }

    /**
     * Fill in this request's message field with a 'get' request.
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     */
    private fun msgGet(ref: String?, collectionName: String?) {
        myMsg = targetVerb("rep", "get")
        myMsg!!.addParameter("tag", myTag)
        val what = type("reqi", EncodeControl.forClient)
        what.addParameter("ref", ref)
        what.addParameter("contents", true)
        what.addParameterOpt("coll", collectionName)
        what.finish()
        myMsg!!.addParameter("what", singleElementArray(what))
        myMsg!!.finish()
    }

    /**
     * Fill in this request's message field with a 'put' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     * configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     */
    private fun msgPut(ref: String?, obj: Encodable, collectionName: String?, requireNew: Boolean) {
        myMsg = targetVerb("rep", "put")
        myMsg!!.addParameter("tag", myTag)
        val what = type("obji", EncodeControl.forClient)
        what.addParameter("ref", ref)
        what.addParameter("obj",
                obj.encode(EncodeControl.forRepository)!!.sendableString())
        what.addParameterOpt("coll", collectionName)
        if (requireNew) {
            what.addParameter("requirenew", requireNew)
        }
        what.finish()
        myMsg!!.addParameter("what", singleElementArray(what))
        myMsg!!.finish()
    }

    /**
     * Fill in this request's message field with an 'update' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param version  Version number of the version of the object to update.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     * configured default (or the db doesn't use this abstraction).
     */
    private fun msgUpdate(ref: String?, version: Int, obj: Encodable, collectionName: String?) {
        myMsg = targetVerb("rep", "update")
        myMsg!!.addParameter("tag", myTag)
        val what = type("updatei", EncodeControl.forClient)
        what.addParameter("ref", ref)
        what.addParameter("version", version)
        what.addParameter("obj",
                obj.encode(EncodeControl.forRepository)!!.sendableString())
        what.addParameterOpt("coll", collectionName)
        what.finish()
        myMsg!!.addParameter("what", singleElementArray(what))
        myMsg!!.finish()
    }

    /**
     * Fill in this request's message field with a 'query' request.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     * configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     */
    private fun msgQuery(template: JsonObject, collectionName: String?, maxResults: Int) {
        myMsg = targetVerb("rep", "query")
        myMsg!!.addParameter("tag", myTag)
        val what = type("queryi", EncodeControl.forClient)
        what.addParameter("template", template)
        what.addParameterOpt("coll", collectionName)
        if (maxResults > 0) {
            what.addParameter("limit", maxResults)
        }
        what.finish()
        myMsg!!.addParameter("what", singleElementArray(what))
        myMsg!!.finish()
    }

    /**
     * Fill in this request's message field with a 'remove' request.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to remove from, or null to
     * take the configured default (or the db doesn't use this abstraction).
     */
    private fun msgRemove(ref: String, collectionName: String?) {
        myMsg = targetVerb("rep", "remove")
        myMsg!!.addParameter("tag", myTag)
        val what = type("reqi", EncodeControl.forClient)
        what.addParameter("ref", ref)
        what.addParameterOpt("coll", collectionName)
        what.finish()
        myMsg!!.addParameter("what", singleElementArray(what))
        myMsg!!.finish()
    }

    /**
     * Return the object reference associated with this request.
     */
    fun ref() = myRef

    /**
     * Transmit the request message to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository.
     */
    fun sendRequest(odbActor: ODBActor) {
        odbActor.send(myMsg!!)
    }

    /**
     * Convenience function to encode an object in a single-element array.
     *
     * @param elem  The object to put in the array.
     *
     * @return a JSONLiteralArray containing the encoded 'elem'.
     */
    private fun singleElementArray(elem: JSONLiteral): JSONLiteralArray {
        val array = JSONLiteralArray()
        array.addElement(elem)
        array.finish()
        return array
    }

    /**
     * Obtain this request's tag string, to match replies with requests.
     *
     * @return this request's tag string.
     */
    fun tag(): String {
        return myTag
    }

    companion object {
        /** Counter for generating request tags.  */
        private var theTagCounter = 0

        /**
         * Generate a request to fetch an object from the repository.
         *
         * @param ref  Reference string naming the object desired.
         * @param collectionName  Name of collection to get from, or null to take
         * the configured default.
         * @param handler  Handler to be called with resulting object.
         *
         * @return an object encapsulating the indicated 'get' request.
         */
        fun getReq(ref: String, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
            val req = PendingRequest(handler, ref, collectionName)
            req.msgGet(ref, collectionName)
            return req
        }

        /**
         * Generate a request to store an object in the repository.
         *
         * @param ref  Reference string naming the object to be put.
         * @param obj  The object itself.
         * @param collectionName  Name of collection to write, or null to take the
         * configured default (or the db doesn't use this abstraction).
         * @param requireNew  If true, require object 'ref' not already exist.
         * @param handler  Handler to be called with result (non)error.
         *
         * @return an object encapsulating the indicated 'put' request.
         */
        fun putReq(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?): PendingRequest {
            val req = PendingRequest(handler, ref, collectionName)
            req.msgPut(ref, obj, collectionName, requireNew)
            return req
        }

        /**
         * Generate a request to update an object in the repository.
         *
         * @param ref  Reference string naming the object to be put.
         * @param version  Version number of the object to be updated.
         * @param obj  The object itself.
         * @param collectionName  Name of collection to write, or null to take the
         * configured default (or the db doesn't use this abstraction).
         * @param handler  Handler to be called with result (non)error.
         *
         * @return an object encapsulating the indicated 'update' request.
         */
        fun updateReq(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
            val req = PendingRequest(handler, ref, collectionName)
            req.msgUpdate(ref, version, obj, collectionName)
            return req
        }

        /**
         * Generate a request to query the object database.
         *
         * @param template  Template object for the objects desired.
         * @param collectionName  Name of collection to query, or null to take the
         * configured default.
         * @param maxResults  Maximum number of result objects to return, or 0 to
         * indicate no fixed limit.
         * @param handler  Handler to be called with the results.
         *
         * @return an object encapsulating the indicated 'query' request.
         */
        fun queryReq(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>?): PendingRequest {
            val req = PendingRequest(handler, "query", collectionName)
            req.msgQuery(template, collectionName, maxResults)
            return req
        }

        /**
         * Generate a request to remove an object from the repository.
         *
         * @param ref  Reference string naming the object to remove.
         * @param collectionName  Name of collection to remove from, or null to
         * take the configured default (or the db doesn't use this abstraction).
         * @param handler  Handler to be called with result.  The result will be
         * either a Boolean indicating normal success or failure, or an
         * exception.
         *
         * @return an object encapsulating the indicated 'remove' request.
         */
        fun removeReq(ref: String, collectionName: String?, handler: Consumer<Any?>?): PendingRequest {
            val req = PendingRequest(handler, ref, collectionName)
            req.msgRemove(ref, collectionName)
            return req
        }
    }

    init {
        myTag = (++theTagCounter).toString()
    }
}