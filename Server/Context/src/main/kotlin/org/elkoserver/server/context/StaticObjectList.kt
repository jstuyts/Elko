package org.elkoserver.server.context

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import java.util.function.Consumer

/**
 * Object stored in the object database that holds a list of static objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the object will be known at runtime
 * in the server, and a ref string, by which the object is stored in the
 * object database.
 */
internal class StaticObjectList @JSONMethod("statics") constructor(private val myStatics: Array<StaticObjectListElem>) {

    /**
     * Fetch the static objects from the object database.
     *
     * @param odb  The object database to tell.
     * @param contextor  Contextor for whom these objects are being loaded
     * @param appTrace  Trace object for logging errors
     */
    fun fetchFromODB(odb: ObjDB, contextor: Contextor, appTrace: Trace) {
        for (elem in myStatics) {
            odb.getObject(elem.ref, null,
                    StaticObjectReceiver(contextor, elem, appTrace))
        }
    }

    private class StaticObjectReceiver internal constructor(var myContextor: Contextor, var myElem: StaticObjectListElem,
                                                            var tr: Trace) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                tr.eventi("loading static object '${myElem.ref}' as '${myElem.key}'")
                myContextor.addStaticObject(myElem.key, obj)
            } else {
                tr.errori("unable to load static object '${myElem.ref}'")
            }
        }

    }

    internal class StaticObjectListElem @JSONMethod("key", "ref") internal constructor(val key: String, val ref: String)
}
