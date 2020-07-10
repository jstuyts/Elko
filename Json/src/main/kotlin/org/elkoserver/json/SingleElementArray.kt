package org.elkoserver.json

/**
 * Convenience function to encode an object in a single-element array.
 *
 * @param elem  The object to put in the array.
 *
 * @return a JSONLiteralArray containing the encoded 'elem'.
 */
fun singleElementArray(elem: JsonLiteral): JsonLiteralArray =
        JsonLiteralArray().apply {
            addElement(elem)
            finish()
        }
