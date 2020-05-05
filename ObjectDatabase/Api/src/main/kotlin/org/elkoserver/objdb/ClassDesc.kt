package org.elkoserver.objdb

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.util.trace.Trace

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
     * @param tr  Trace object for error logging.
     */
    fun useInODB(odb: ObjDB, tr: Trace) {
        for (odbClass in myClasses) {
            odbClass.useInODB(odb, tr)
        }
    }
}
