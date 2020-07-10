package org.elkoserver.json

/**
 * Convert a value of any type into the appropriate characters appended
 * onto the string under construction.
 *
 * @param buf  Buffer into which to encode the given value.
 * @param value  The value whose string encoding is sought
 * @param control  Encode control determining what flavor of encoding
 * is being done.
 *
 * @return true if the given value could not be encoded and so should be
 * ignored, false if everything worked fine.
 */
internal fun appendValueString(buf: StringBuilder, value: Any?, control: EncodeControl): Boolean {
    if (value == null) {
        /* Null is a special value all its own */
        buf.append("null")
    } else if (value is String) {
        buf.append('"')
        var start = 0
        for (i in 0 until value.length) {
            val c = value[i]
            var escape = '*'
            when (c) {
                '"' -> escape = '"'
                '\\' -> escape = '\\'
                '\b' -> escape = 'b'
                '\u000C' -> escape = 'f'
                '\n' -> escape = 'n'
                '\r' -> escape = 'r'
                '\t' -> escape = 't'
            }
            if (escape != '*') {
                buf.append(value, start, i)
                start = i + 1
                buf.append('\\')
                buf.append(escape)
            }
        }
        buf.append(value.substring(start))
        buf.append('"')
    } else if (value is Number) {
        buf.append(value.toString())
    } else if (value is Encodable) {
        /* If the value knows how, ask it to encode itself */
        val encoded = value.encode(control)
        if (encoded != null) {
            val result = encoded.sendableString()
            buf.append(result)
        } else {
            return true
        }
    } else if (value is JsonLiteral) {
        buf.append(value.myStringBuilder)
    } else if (value is JsonLiteralArray) {
        buf.append(value.stringBuilder)
    } else if (value is JsonObject) {
        JsonObjectSerialization.encodeLiteral(value, buf, control)
    } else if (value is JsonArray) {
        JsonArraySerialization.encodeLiteral(value, buf, control)
    } else {
        /* Else just convert the value to its natural string form */
        buf.append(value.toString())
    }
    return false
}
