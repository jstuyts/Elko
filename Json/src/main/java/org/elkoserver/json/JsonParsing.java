package org.elkoserver.json;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.io.IOException;
import java.io.Reader;

public class JsonParsing {
    public static JsonObject jsonObjectFromString(String string) throws JsonParserException {
        JsonObject result;

        if (string.isEmpty()) {
            result = null;
        } else {
            com.grack.nanojson.JsonObject nanoObject = JsonParser.object().from(string);
            result = nanoObject == null ? null : new JsonObject(nanoObject);
        }

        return result;
    }

    public static JsonObject jsonObjectFromReader(Reader reader) throws JsonParserException {
        JsonObject result;

        if (!reader.markSupported()) {
            throw new IllegalStateException();
        }

        int nextCharacter;
        try {
            reader.mark(1);
            nextCharacter = reader.read();
            reader.reset();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (nextCharacter == -1) {
            result = null;
        } else {
            com.grack.nanojson.JsonObject nanoObject = JsonParser.object().from(reader);
            result = nanoObject == null ? null : new JsonObject(nanoObject);
        }

        return result;
    }
}
