package org.elkoserver.server.context.statics;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple extendsion of HashMap (mapping String->JsonObject) so it can be
 * loaded as a static object from the object store.
 */
class StaticObjectMap extends HashMap<String, JsonObject> {
    /**
     * JSON-driven constructor.
     *
     * @param map  JSON object will be interpreted as a mapping from String to
     *    JsonObject.
     */
    @JSONMethod({ "map" })
    StaticObjectMap(JsonObject map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(entry.getKey(), (JsonObject) entry.getValue());
        }
    }
}
