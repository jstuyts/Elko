package org.elkoserver.objdb

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.slf4j.Gorgel

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
     */
    fun useInODB(odb: ObjDB, gorgel: Gorgel) {
        try {
            odb.addClass(myTag, Class.forName(myClassName))
        } catch (e: ClassNotFoundException) {
            gorgel.error("unable to load class info for '$myTag': class ${e.message} not found")
        }
    }
}
