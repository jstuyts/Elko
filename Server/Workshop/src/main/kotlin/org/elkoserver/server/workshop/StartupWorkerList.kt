package org.elkoserver.server.workshop

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import java.util.function.Consumer

/**
 * Object stored in the object database that holds a list of worker objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the worker object will be known at
 * runtime in the server, and a ref string, by which the object is stored in
 * the object database.  By convention these two are the same but they need not
 * be.
 */
internal class StartupWorkerList @JSONMethod("workers") constructor(private val myWorkers: Array<WorkerListElem>) {

    /**
     * Fetch the worker objects from the object database.
     *
     * @param odb  The object database to tell.
     * @param workshop  Workshop for whom these objects are being loaded
     * @param appTrace  Trace object for logging errors
     */
    fun fetchFromODB(odb: ObjDB, workshop: Workshop, appTrace: Trace) {
        myWorkers.forEach { elem ->
            odb.getObject(elem.ref, null, WorkerReceiver(workshop, elem, appTrace))
        }
    }

    private class WorkerReceiver internal constructor(var myWorkshop: Workshop, var myElem: WorkerListElem, var tr: Trace) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                if (obj is WorkerObject) {
                    tr.eventi("loading worker object '${myElem.ref}' as '${myElem.key}'")
                    myWorkshop.addWorkerObject(myElem.key, obj)
                } else {
                    tr.errori("alleged worker object '${myElem.ref}' is not actually a WorkerObject, ignoring it")
                }
            } else {
                tr.errori("unable to load worker object '${myElem.ref}'")
            }
        }
    }

    internal class WorkerListElem @JSONMethod("key", "ref") internal constructor(val key: String, val ref: String)
}
