package org.elkoserver.objdb

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.Trace

/**
 * Object stored in the object database that keeps track of the mapping between
 * a class tag (as used in the object database) and the actual Java class that
 * it corresponds to.
 */
internal class ClassTagDesc @JSONMethod("tag", "name") constructor(private val myTag: String, private val myClassName: String) {

    /**
     * Tell an object database that about the class this object describes.
     *
     * @param odb  The object database to tell.
     * @param tr  Trace object for error logging.
     */
    fun useInODB(odb: ObjDB, tr: Trace) {
        try {
            odb.addClass(myTag, Class.forName(myClassName))
        } catch (e: ClassNotFoundException) {
            tr.errorm("unable to load class info for '$myTag': class ${e.message} not found")
        }
    }
}
