package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * Object stored in the object database that keeps track of the mapping between
 * all the class tags known to the object database and the actual Java classes
 * that they correspond to.
 */
internal class ClassDesc @JsonMethod("classes") constructor(private val myClasses: Array<ClassTagDesc>) {

    /**
     * Tell an object database about all the classes this object describes.
     *
     * @param objectDatabase The object database to tell.
     */
    fun useInObjectDatabase(objectDatabase: ObjectDatabase, gorgel: Gorgel) {
        for (odbClass in myClasses) {
            odbClass.useInObjectDatabase(objectDatabase, gorgel)
        }
    }
}
