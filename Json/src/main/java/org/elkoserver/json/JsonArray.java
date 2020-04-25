package org.elkoserver.json;

import java.util.Collection;
import java.util.Iterator;

import static org.elkoserver.json.JsonWrapping.wrapWithElkoJsonImplementationIfNeeded;

// FIXME: This class is here because:
// - The toString() has complex behavior. Not sure if this is only for diagnostic purposes or for production use.
public class JsonArray implements Iterable<Object> {
    final com.grack.nanojson.JsonArray impl;

    public JsonArray() {
        super();

        impl = new com.grack.nanojson.JsonArray();
    }

    public JsonArray(Collection<?> collection) {
        super();

        impl = new com.grack.nanojson.JsonArray(collection);
    }

    @Override
    public Iterator<Object> iterator() {
        return new JsonArrayIterator(impl.iterator());
    }

    public String toString() {
        return JsonArraySerialization.literal(this, EncodeControl.forRepository).sendableString();
    }

    public Object[] toArray() {
        Object[] result = impl.toArray();

        for (int i = 0; i < result.length; i++) {
            result[i] = wrapWithElkoJsonImplementationIfNeeded(result[i]);
        }

        return result;
    }

    public int size() {
        return impl.size();
    }

    public void add(Object elem) {
        impl.add(wrapWithElkoJsonImplementationIfNeeded(elem));
    }
}

class JsonArrayIterator implements Iterator<Object> {
    private final Iterator<Object> impl;

    public JsonArrayIterator(Iterator<Object> impl) {
        this.impl = impl;
    }

    @Override
    public boolean hasNext() {
        return impl.hasNext();
    }

    @Override
    public Object next() {
        return wrapWithElkoJsonImplementationIfNeeded(impl.next());
    }

}