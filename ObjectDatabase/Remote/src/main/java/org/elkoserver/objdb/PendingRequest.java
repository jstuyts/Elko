package org.elkoserver.objdb;

import org.elkoserver.json.*;

import java.util.function.Consumer;

import static org.elkoserver.json.JSONLiteralFactory.targetVerb;
import static org.elkoserver.json.JSONLiteralFactory.type;

/**
 * A pending request to the repository.
 */
class PendingRequest {
    /** Counter for generating request tags. */
    private static int theTagCounter = 0;

    /** Tag to match request with reply */
    private String myTag;

    /** Object reference associated with request. */
    private String myRef;

    /** Encoded request message. */
    private JSONLiteral myMsg;

    /** Collection to operate on. */
    private String myCollectionName;

    /** Handler to be called with request result when available. */
    private Consumer<Object> myHandler;

    /**
     * Private constructor.  N.b.: initially, the object has no encoded message
     * associated with it and thus should not be used until the message is
     * filled in by one of the msgXxx calls.
     *
     * @param handler  Handler to call on the request result.
     * @param ref  Object reference being operated on.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default.
     */
    private PendingRequest(Consumer<Object> handler, String ref,
                           String collectionName) {
        myTag = Integer.toString(++theTagCounter);
        myHandler = handler;
        myRef = ref;
        myCollectionName = collectionName;
        myMsg = null;
    }

    /**
     * Generate a request to fetch an object from the repository.
     *
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default.
     * @param handler  Handler to be called with resulting object.
     *
     * @return an object encapsulating the indicated 'get' request.
     */
    static PendingRequest getReq(String ref, String collectionName,
                                 Consumer<Object> handler) {
        PendingRequest req = new PendingRequest(handler, ref, collectionName);
        req.msgGet(ref, collectionName);
        return req;
    }

    /**
     * Handle a reply from the repository.
     *
     * @param obj  The reply object.
     */
    void handleReply(Object obj) {
        if (myHandler != null) {
            myHandler.accept(obj);
        }
    }

    /**
     * Fill in this request's message field with a 'get' request.
     *  @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     */
    private void msgGet(String ref, String collectionName) {
        myMsg = targetVerb("rep", "get");
        myMsg.addParameter("tag", myTag);

        JSONLiteral what = type("reqi", EncodeControl.forClient);
        what.addParameter("ref", ref);
        what.addParameter("contents", true);
        what.addParameterOpt("coll", collectionName);
        what.finish();

        myMsg.addParameter("what", singleElementArray(what));
        myMsg.finish();
    }

    /**
     * Fill in this request's message field with a 'put' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     *    configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     */
    private void msgPut(String ref, Encodable obj, String collectionName,
                        boolean requireNew)
    {
        myMsg = targetVerb("rep", "put");
        myMsg.addParameter("tag", myTag);

        JSONLiteral what = type("obji", EncodeControl.forClient);
        what.addParameter("ref", ref);
        what.addParameter("obj",
            obj.encode(EncodeControl.forRepository).sendableString());
        what.addParameterOpt("coll", collectionName);
        if (requireNew) {
            what.addParameter("requirenew", requireNew);
        }
        what.finish();

        myMsg.addParameter("what", singleElementArray(what));
        myMsg.finish();
    }

    /**
     * Fill in this request's message field with an 'update' request.
     *
     * @param ref  Reference string naming the object to be put.
     * @param version  Version number of the version of the obejct to update.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     *    configured default (or the db doesn't use this abstraction).
     */
    private void msgUpdate(String ref, int version, Encodable obj,
                           String collectionName)
    {
        myMsg = targetVerb("rep", "update");
        myMsg.addParameter("tag", myTag);

        JSONLiteral what = type("updatei", EncodeControl.forClient);
        what.addParameter("ref", ref);
        what.addParameter("version", version);
        what.addParameter("obj",
            obj.encode(EncodeControl.forRepository).sendableString());
        what.addParameterOpt("coll", collectionName);
        what.finish();

        myMsg.addParameter("what", singleElementArray(what));
        myMsg.finish();
    }

    /**
     * Fill in this request's message field with a 'query' request.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     */
    private void msgQuery(JsonObject template, String collectionName,
                          int maxResults) {
        myMsg = targetVerb("rep", "query");
        myMsg.addParameter("tag", myTag);

        JSONLiteral what = type("queryi", EncodeControl.forClient);
        what.addParameter("template", template);
        what.addParameterOpt("coll", collectionName);
        if (maxResults > 0) {
            what.addParameter("limit", maxResults);
        }
        what.finish();

        myMsg.addParameter("what", singleElementArray(what));
        myMsg.finish();
    }

    /**
     * Fill in this request's message field with a 'remove' request.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to remove from, or null to
     *    take the configured default (or the db doesn't use this abstraction).
     */
    private void msgRemove(String ref, String collectionName) {
        myMsg = targetVerb("rep", "remove");
        myMsg.addParameter("tag", myTag);

        JSONLiteral what = type("reqi", EncodeControl.forClient);
        what.addParameter("ref", ref);
        what.addParameterOpt("coll", collectionName);
        what.finish();

        myMsg.addParameter("what", singleElementArray(what));
        myMsg.finish();
    }

    /**
     * Generate a request to store an object in the repository.
     *
     * @param ref  Reference string naming the object to be put.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     *    configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     * @param handler  Handler to be called with result (non)error.
     *
     * @return an object encapsulating the indicated 'put' request.
     */
    static PendingRequest putReq(String ref, Encodable obj,
                                 String collectionName, boolean requireNew,
                                 Consumer<Object> handler)
    {
        PendingRequest req = new PendingRequest(handler, ref, collectionName);
        req.msgPut(ref, obj, collectionName, requireNew);
        return req;
    }

    /**
     * Generate a request to update an object in the repository.
     *
     * @param ref  Reference string naming the object to be put.
     * @param version  Version number of the object to be updated.
     * @param obj  The object itself.
     * @param collectionName  Name of collection to write, or null to take the
     *    configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with result (non)error.
     *
     * @return an object encapsulating the indicated 'update' request.
     */
    static PendingRequest updateReq(String ref, int version, Encodable obj,
                                    String collectionName, Consumer<Object> handler)
    {
        PendingRequest req = new PendingRequest(handler, ref, collectionName);
        req.msgUpdate(ref, version, obj, collectionName);
        return req;
    }

    /**
     * Generate a request to query the object database.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     * @param handler  Handler to be called with the results.
     *
     * @return an object encapsulating the indicated 'query' request.
     */
    static PendingRequest queryReq(JsonObject template, String collectionName,
                                   int maxResults, Consumer<Object> handler) {
        PendingRequest req =
            new PendingRequest(handler, "query", collectionName);
        req.msgQuery(template, collectionName, maxResults);
        return req;
    }

    /**
     * Return the object reference associated with this request.
     */
    String ref() {
        return myRef;
    }

    /**
     * Generate a request to remove an object from the repository.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to remove from, or null to
     *    take the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with result.  The result will be
     *   either a Boolean indicating normal success or failure, or an
     *   exception.
     *
     * @return an object encapsulating the indicated 'remove' request.
     */
    static PendingRequest removeReq(String ref, String collectionName,
                                    Consumer<Object> handler) {
        PendingRequest req = new PendingRequest(handler, ref, collectionName);
        req.msgRemove(ref, collectionName);
        return req;
    }

    /**
     * Transmit the request message to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository.
     */
    void sendRequest(ODBActor odbActor) {
        odbActor.send(myMsg);
    }

    /**
     * Convenience function to encode an object in a single-element array.
     *
     * @param elem  The object to put in the array.
     *
     * @return a JSONLiteralArray containing the encoded 'elem'.
     */
    private JSONLiteralArray singleElementArray(JSONLiteral elem) {
        JSONLiteralArray array = new JSONLiteralArray();
        array.addElement(elem);
        array.finish();
        return array;
    }

    /**
     * Obtain this request's tag string, to match replies with requests.
     *
     * @return this request's tag string.
     */
    String tag() {
        return myTag;
    }
}
