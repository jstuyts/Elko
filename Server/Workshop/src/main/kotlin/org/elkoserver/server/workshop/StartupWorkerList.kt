package org.elkoserver.server.workshop

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

/**
 * Object stored in the object database that holds a list of worker objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the worker object will be known at
 * runtime in the server, and a ref string, by which the object is stored in
 * the object database.  By convention these two are the same but they need not
 * be.
 */
internal class StartupWorkerList @JsonMethod("workers") constructor(private val myWorkers: Array<WorkerListElem>) {

    /**
     * Fetch the worker objects from the object database.
     *
     * @param objectDatabase  The object database to tell.
     * @param workshop  Workshop for whom these objects are being loaded
     */
    fun fetchFromObjectDatabase(objectDatabase: ObjectDatabase, workshop: Workshop, gorgel: Gorgel) {
        myWorkers.forEach { elem ->
            objectDatabase.getObject(elem.ref, null, WorkerReceiver(workshop, elem, gorgel))
        }
    }

    private class WorkerReceiver(var myWorkshop: Workshop, var myElem: WorkerListElem, var gorgel: Gorgel) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                if (obj is WorkerObject) {
                    gorgel.i?.run { info("loading worker object '${myElem.ref}' as '${myElem.key}'") }
                    myWorkshop.addWorkerObject(myElem.key, obj)
                } else {
                    gorgel.error("alleged worker object '${myElem.ref}' is not actually a WorkerObject, ignoring it")
                }
            } else {
                gorgel.error("unable to load worker object '${myElem.ref}'")
            }
        }
    }

    internal class WorkerListElem @JsonMethod("key", "ref") internal constructor(val key: String, val ref: String)
}
