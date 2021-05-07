package org.elkoserver.objectdatabase

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.objectdatabase.store.ObjectStore
import org.elkoserver.objectdatabase.store.ObjectStoreArguments
import org.elkoserver.util.trace.slf4j.Gorgel
import java.lang.reflect.InvocationTargetException

object ObjectStoreFactory {
    fun createAndInitializeObjectStore(props: ElkoProperties, propRoot: String, baseGorgel: Gorgel): ObjectStore {
        val objectStoreClassName = props.getProperty("$propRoot.objstore",
                "org.elkoserver.objectdatabase.store.filestore.FileObjectStore")
        val objectStoreClass = try {
            Class.forName(objectStoreClassName)
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("object store class $objectStoreClassName not found", e)
        }
        return try {
            val constructor = objectStoreClass.getConstructor(ObjectStoreArguments::class.java)
            val arguments = ObjectStoreArguments(props, propRoot, baseGorgel.getChild(objectStoreClass.kotlin))
            constructor.newInstance(arguments) as ObjectStore
        } catch (e: IllegalAccessException) {
            throw IllegalStateException("unable to access object store constructor", e)
        } catch (e: InstantiationException) {
            throw IllegalStateException("unable to instantiate object store object", e)
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("unable to find object store constructor", e)
        } catch (e: InvocationTargetException) {
            throw IllegalStateException("error during invocation of object store constructor", e)
        }
    }
}