package org.elkoserver.server.context

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

/**
 * Object stored in the object database that holds a list of static objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the object will be known at runtime
 * in the server, and a ref string, by which the object is stored in the
 * object database.
 */
internal class StaticObjectList @JsonMethod("statics") constructor(private val myStatics: Array<StaticObjectListElem>) {

    /**
     * Fetch the static objects from the object database.
     *
     * @param objectDatabase  The object database to tell.
     * @param contextor  Contextor for whom these objects are being loaded
     */
    fun fetchFromObjectDatabase(objectDatabase: ObjectDatabase, contextor: Contextor, gorgel: Gorgel) {
        for (elem in myStatics) {
            objectDatabase.getObject(elem.ref, null,
                    StaticObjectReceiver(contextor, elem, gorgel))
        }
    }

    private class StaticObjectReceiver(var myContextor: Contextor, var myElem: StaticObjectListElem,
                                       var gorgel: Gorgel) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                gorgel.i?.run { info("loading static object '${myElem.ref}' as '${myElem.key}'") }
                myContextor.addStaticObject(myElem.key, obj)
            } else {
                gorgel.error("unable to load static object '${myElem.ref}'")
            }
        }

    }

    internal class StaticObjectListElem @JsonMethod("key", "ref") internal constructor(val key: String, val ref: String)
}
