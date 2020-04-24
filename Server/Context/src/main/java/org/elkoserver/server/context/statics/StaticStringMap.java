package org.elkoserver.server.context.statics;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple extension of HashMap (mapping String->String) so it can be loaded
 * as a static object from the object store.
 */
class StaticStringMap extends HashMap<String, String> {
    /**
     * JSON-driven constructor.
     *
     * @param map  JSON object will be interpreted as a mapping from String to
     *    String.
     */
    @JSONMethod({ "map" })
    StaticStringMap(JsonObject map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(entry.getKey(), (String) entry.getValue());
        }
    }
}
