package org.elkoserver.objdb

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Object stored in the object database that keeps track of the mapping between
 * all the class tags known to the object database and the actual Java classes
 * that they correspond to.
 */
internal class ClassDesc @JSONMethod("classes") constructor(private val myClasses: Array<ClassTagDesc>) {

    /**
     * Tell an object database about all the classes this object describes.
     *
     * @param odb The object database to tell.
     */
    fun useInODB(odb: ObjDB, gorgel: Gorgel) {
        for (odbClass in myClasses) {
            odbClass.useInODB(odb, gorgel)
        }
    }
}
