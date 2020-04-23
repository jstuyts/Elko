package org.elkoserver.json;

public class JsonWrapping {
    public static Object wrapWithElkoJsonImplementationIfNeeded(Object object) {
        Object result;

        if (object instanceof com.grack.nanojson.JsonObject) {
            result = new JsonObject((com.grack.nanojson.JsonObject) object);
        } else if (object instanceof com.grack.nanojson.JsonArray) {
            result = new JsonArray((com.grack.nanojson.JsonArray) object);
        } else {
            result = object;
        }

        return result;
    }
}
