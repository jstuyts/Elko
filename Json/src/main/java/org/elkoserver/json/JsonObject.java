package org.elkoserver.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.elkoserver.json.JsonWrapping.wrapWithElkoJsonImplementationIfNeeded;

// FIXME: This class is here because:
// - The toString() has complex behavior. Not sure if this is only for diagnostic purposes or for production use.
// - The behavior of getters without default value.
public class JsonObject {
    private final com.grack.nanojson.JsonObject impl;

    public JsonObject() {
        super();

        impl = new com.grack.nanojson.JsonObject();
    }

    public JsonObject(Map<? extends String, ?> map) {
        super();

        Map<String, Object> elkoMap = new HashMap<>();
        map.forEach((key, value) -> elkoMap.put(key, wrapWithElkoJsonImplementationIfNeeded(value)));

        impl = new com.grack.nanojson.JsonObject(elkoMap);
    }

    public String toString() {
        return JsonObjectSerialization.literal(this, EncodeControl.forRepository).sendableString();
    }

    public String getString(String key) throws JSONDecodingException {
        if (!(impl.get(key) instanceof String)) {
            throw new JSONDecodingException();
        }
        return impl.getString(key);
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return impl.entrySet();
    }

    public void put(String key, Object value) {
        impl.put(key, wrapWithElkoJsonImplementationIfNeeded(value));
    }

    public JsonObject getObject(String key, JsonObject defaultValue) {
        return (JsonObject) wrapWithElkoJsonImplementationIfNeeded(impl.getObject(key, defaultValue == null ? null : defaultValue.impl));
    }

    public String getString(String key, String defaultValue) {
        return impl.getString(key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return impl.getDouble(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return impl.getInt(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return impl.getBoolean(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return impl.getLong(key, defaultValue);
    }

    public JsonObject getObject(String key) throws JSONDecodingException {
        if (!(impl.get(key) instanceof JsonObject) && !(impl.get(key) instanceof com.grack.nanojson.JsonObject)) {
            throw new JSONDecodingException();
        }
        return (JsonObject) wrapWithElkoJsonImplementationIfNeeded(impl.getObject(key));
    }

    public int getInt(String key) throws JSONDecodingException {
        if (!(impl.get(key) instanceof Integer)) {
            throw new JSONDecodingException();
        }
        return impl.getInt(key);
    }

    public JsonArray getArray(String key, JsonArray defaultValue) {
        return (JsonArray) wrapWithElkoJsonImplementationIfNeeded(impl.getArray(key, defaultValue == null ? null : defaultValue.impl));
    }
}
