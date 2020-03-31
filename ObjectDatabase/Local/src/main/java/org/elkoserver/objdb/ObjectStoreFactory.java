package org.elkoserver.objdb;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.objdb.store.ObjectStore;
import org.elkoserver.util.trace.Trace;

import java.lang.reflect.InvocationTargetException;

public class ObjectStoreFactory {
    public static ObjectStore createAndInitializeObjectStore(BootProperties props, String propRoot, Trace trace) {
        ObjectStore result;

        String objectStoreClassName = props.getProperty(propRoot + ".objstore",
                "org.elkoserver.objdb.store.filestore.FileObjectStore");
        Class<?> objectStoreClass;
        try {
            objectStoreClass = Class.forName(objectStoreClassName);
        } catch (ClassNotFoundException e) {
            trace.fatalError("object store class " + objectStoreClassName + " not found");
            throw new IllegalStateException();
        }
        try {
            result = (ObjectStore) objectStoreClass.getConstructor().newInstance();
        } catch (IllegalAccessException e) {
            trace.fatalError("unable to access object store constructor: " + e);
            throw new IllegalStateException();
        } catch (InstantiationException e) {
            trace.fatalError("unable to instantiate object store object: " + e);
            throw new IllegalStateException();
        } catch (NoSuchMethodException e) {
            trace.fatalError("unable to find object store constructor: " + e);
            throw new IllegalStateException();
        } catch (InvocationTargetException e) {
            trace.fatalError("error during invocation of object store constructor: " + e.getCause());
            throw new IllegalStateException();
        }
        result.initialize(props, propRoot, trace);

        return result;
    }
}
