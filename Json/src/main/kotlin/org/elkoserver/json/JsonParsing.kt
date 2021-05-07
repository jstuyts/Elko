package org.elkoserver.json

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.io.Reader

object JsonParsing {
    @Throws(JsonParserException::class)
    fun jsonObjectFromString(string: String) =
            if (string.isEmpty()) {
                null
            } else {
                JsonParser.`object`().from(string)
            }

    @Throws(JsonParserException::class)
    fun jsonObjectFromReader(reader: Reader): JsonObject? {
        val result: JsonObject?
        check(reader.markSupported())
        val nextCharacter: Int
        try {
            reader.mark(1)
            nextCharacter = reader.read()
            reader.reset()
        } catch (e: IOException) {
            throw IllegalArgumentException(e)
        }
        result = if (nextCharacter == -1) {
            null
        } else {
            val nanoObject = JsonParser.`object`().from(reader)
            nanoObject?.let(::JsonObject)
        }
        return result
    }
}
