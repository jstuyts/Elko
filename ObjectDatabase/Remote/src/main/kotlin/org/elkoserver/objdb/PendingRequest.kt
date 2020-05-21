package org.elkoserver.objdb

import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl.Companion.forClient
import org.elkoserver.json.EncodeControl.Companion.forRepository
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
 * @param ref  Object reference being operated on.
 * @param myCollectionName  Name of collection to get from, or null to take
 *    the configured default.
 */
internal class PendingRequest private constructor(private val myHandler: Consumer<Any?>?, internal val ref: String, private val myCollectionName: String?, messageProvider: (String) -> JSONLiteral) {
    /** Tag to match request with reply  */
    internal val tag: String

    /** Encoded request message.  */
    private val myMsg: JSONLiteral

    /**
     * Handle a reply from the repository.
     *
     * @param obj  The reply object.
     */
    fun handleReply(obj: Any?) {
        myHandler?.accept(obj)
    }

    /**
     * Transmit the request message to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository.
     */
    fun sendRequest(odbActor: ODBActor) {
        odbActor.send(myMsg)
    }

    companion object {
        /** Counter for generating request tags.  */
        @Deprecated("Global variable")
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
        fun getReq(ref: String, collectionName: String?, handler: Consumer<Any?>?) =
                PendingRequest(handler, ref, collectionName) { tag ->
                    msgGet(ref, tag, collectionName)
                }

        /**
         * Fill in this request's message field with a 'get' request.
         * @param ref  Reference string naming the object desired.
         * @param collectionName  Name of collection to get from, or null to take
         */
        private fun msgGet(ref: String, tag: String, collectionName: String?) =
                targetVerb("rep", "get").apply {
                    addParameter("tag", tag)
                    val what = type("reqi", forClient).apply {
                        addParameter("ref", ref)
                        addParameter("contents", true)
                        addParameterOpt("coll", collectionName)
                        finish()
                    }
                    addParameter("what", singleElementArray(what))
                    finish()
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
        fun putReq(ref: String, obj: Encodable, collectionName: String?, requireNew: Boolean, handler: Consumer<Any?>?) =
                PendingRequest(handler, ref, collectionName) { tag ->
                    msgPut(ref, tag, obj, collectionName, requireNew)
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
        private fun msgPut(ref: String, tag: String, obj: Encodable, collectionName: String?, requireNew: Boolean) =
                targetVerb("rep", "put").apply {
                    addParameter("tag", tag)
                    val what = type("obji", forClient).apply {
                        addParameter("ref", ref)
                        addParameter("obj", obj.encode(forRepository)!!.sendableString())
                        addParameterOpt("coll", collectionName)
                        if (requireNew) {
                            addParameter("requirenew", requireNew)
                        }
                        finish()
                    }
                    addParameter("what", singleElementArray(what))
                    finish()
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
        fun updateReq(ref: String, version: Int, obj: Encodable, collectionName: String?, handler: Consumer<Any?>?) =
                PendingRequest(handler, ref, collectionName) { tag ->
                    msgUpdate(ref, tag, version, obj, collectionName)
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
        private fun msgUpdate(ref: String, tag: String, version: Int, obj: Encodable, collectionName: String?) =
                targetVerb("rep", "update").apply {
                    addParameter("tag", tag)
                    val what = type("updatei", forClient).apply {
                        addParameter("ref", ref)
                        addParameter("version", version)
                        addParameter("obj", obj.encode(forRepository)!!.sendableString())
                        addParameterOpt("coll", collectionName)
                        finish()
                    }
                    addParameter("what", singleElementArray(what))
                    finish()
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
        fun queryReq(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>?) =
                PendingRequest(handler, "query", collectionName) { tag ->
                    msgQuery(template, tag, collectionName, maxResults)
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
        private fun msgQuery(template: JsonObject, tag: String, collectionName: String?, maxResults: Int) =
                targetVerb("rep", "query").apply {
                    addParameter("tag", tag)
                    val what = type("queryi", forClient).apply {
                        addParameter("template", template)
                        addParameterOpt("coll", collectionName)
                        if (maxResults > 0) {
                            addParameter("limit", maxResults)
                        }
                        finish()
                    }
                    addParameter("what", singleElementArray(what))
                    finish()
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
        fun removeReq(ref: String, collectionName: String?, handler: Consumer<Any?>?) =
                PendingRequest(handler, ref, collectionName) { tag ->
                    msgRemove(ref, tag, collectionName)
                }

        /**
         * Fill in this request's message field with a 'remove' request.
         *
         * @param ref  Reference string naming the object to remove.
         * @param collectionName  Name of collection to remove from, or null to
         * take the configured default (or the db doesn't use this abstraction).
         */
        private fun msgRemove(ref: String, tag: String, collectionName: String?) =
                targetVerb("rep", "remove").apply {
                    addParameter("tag", tag)
                    val what = type("reqi", forClient).apply {
                        addParameter("ref", ref)
                        addParameterOpt("coll", collectionName)
                        finish()
                    }
                    addParameter("what", singleElementArray(what))
                    finish()
                }

        /**
         * Convenience function to encode an object in a single-element array.
         *
         * @param elem  The object to put in the array.
         *
         * @return a JSONLiteralArray containing the encoded 'elem'.
         */
        private fun singleElementArray(elem: JSONLiteral) =
                JSONLiteralArray().apply {
                    addElement(elem)
                    finish()
                }
    }

    init {
        // FIXME: tag is created inside the constructor, and then the message is created. Create the tag and message
        // outside and pass both as a parameter.
        tag = (++theTagCounter).toString()
        myMsg = messageProvider(tag)
    }
}
