package org.elkoserver.objectdatabase

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.json.TypeResolver
import org.elkoserver.json.Encodable
import java.util.function.Consumer

/**
 * Asynchronous interface to the object database.
 */
interface ObjectDatabase : TypeResolver {
    /**
     * Inform the object database about a mapping from a JSON object type tag
     * string to a Java class.
     *
     * @param tag  The JSON object type tag string.
     * @param type  The class that 'tag' labels.
     */
    fun addClass(tag: String, type: Class<*>)

    /**
     * Fetch an object from the object database.
     *
     * @param ref  Reference string naming the object desired.
     * @param handler  Handler to be called with the result.  The result will
     * be the object requested, or null if the object could not be
     * retrieved.
     */
    fun getObject(ref: String, handler: Consumer<Any?>)

    /**
     * Store an object into the object database.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    fun putObject(ref: String, obj: Encodable, handler: Consumer<Any?>?)

    /**
     * Query one or more objects from the object database.
     *
     * @param template  Template object for the objects desired.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     * be an array of the object(s) requested, or null if no objects could
     * be retrieved.
     */
    fun queryObjects(template: JsonObject, maxResults: Int, handler: Consumer<Any?>)

    /**
     * Delete an object from the object database.  It is not considered an
     * error to attempt to remove an object that is not there; such an
     * operation always succeeds.
     *
     * @param ref  Reference string naming the object to remove.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    fun removeObject(ref: String, handler: Consumer<Any?>?)

    /**
     * Shutdown the object database.
     */
    fun shutDown()

    /**
     * Update an object in the object database.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be updated.
     * @param obj  The object to be stored.
     * @param handler  Handler to be called with the result.  The result will
     * be a status indicator: an error message string if there was an error,
     * or null if the operation was successful.
     */
    fun updateObject(ref: String, version: Int, obj: Encodable, handler: Consumer<Any?>?)
}
