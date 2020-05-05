package org.elkoserver.objdb

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.objdb.store.ObjectStore
import org.elkoserver.util.trace.Trace
import java.lang.reflect.InvocationTargetException

object ObjectStoreFactory {
    fun createAndInitializeObjectStore(props: ElkoProperties, propRoot: String, trace: Trace): ObjectStore {
        val result: ObjectStore
        val objectStoreClassName = props.getProperty("$propRoot.objstore",
                "org.elkoserver.objdb.store.filestore.FileObjectStore")
        val objectStoreClass: Class<*>
        objectStoreClass = try {
            Class.forName(objectStoreClassName)
        } catch (e: ClassNotFoundException) {
            trace.fatalError("object store class $objectStoreClassName not found")
        }
        result = try {
            objectStoreClass.getConstructor().newInstance() as ObjectStore
        } catch (e: IllegalAccessException) {
            trace.fatalError("unable to access object store constructor: $e")
        } catch (e: InstantiationException) {
            trace.fatalError("unable to instantiate object store object: $e")
        } catch (e: NoSuchMethodException) {
            trace.fatalError("unable to find object store constructor: $e")
        } catch (e: InvocationTargetException) {
            trace.fatalError("error during invocation of object store constructor: " + e.cause)
        }
        result.initialize(props, propRoot, trace)
        return result
    }
}