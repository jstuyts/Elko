package org.elkoserver.foundation.json

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.elkoserver.json.JsonParsing
import org.elkoserver.json.getStringOrNull
import org.elkoserver.util.trace.slf4j.Gorgel

class JsonToObjectDeserializer(
        private val gorgel: Gorgel,
        private val constructorInvokerCommGorgel: Gorgel,
        private val injectors: Collection<Injector> = emptyList()) {
    /** Mapping from Java class to the specific decoder for that class.  This
     * is a cache of decoders, to avoid recomputing reflection information.  */
    private val theDecoders: MutableMap<Class<*>, ObjectDecoder> = HashMap()

    /**
     * Obtain (by looking it up in theDecoders or by creating it) an
     * ObjectDecoder for a given class.
     *
     * @param decodeClass  The class whose decoder is sought
     *
     * @return a decoder for 'decodeClass', or null if one could not be made.
     */
    private fun classDecoder(decodeClass: Class<*>): ObjectDecoder? {
        var decoder = theDecoders[decodeClass]
        if (decoder == null) {
            try {
                decoder = ObjectDecoder(decodeClass, constructorInvokerCommGorgel, this, injectors)
                theDecoders[decodeClass] = decoder
            } catch (e: JsonSetupError) {
                gorgel.error(e.message ?: e.toString())
                decoder = null
            }
        }
        return decoder
    }

    /**
     * Produce the Java object described by a particular JSON object
     * descriptor.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     * result will not necessarily be of this class, but will be assignable
     * to a variable of this class.
     * @param obj  The parsed JSON object descriptor to be decoded.
     * @param resolver  An object mapping type tag strings to Java classes.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     * described by 'obj', or null if the object could not be decoded for
     * some reason.
     */
    fun decode(baseType: Class<*>, obj: JsonObject, resolver: TypeResolver): Any? {
        var result: Any? = null
        val typeName = obj.getStringOrNull("type")
        val targetClass: Class<*>?
        if (typeName != null) {
            targetClass = resolver.resolveType(baseType, typeName)
            if (targetClass == null) {
                gorgel.error("no Java class associated with JSON type tag '$typeName'")
            }
        } else {
            targetClass = baseType
        }
        if (targetClass != null) {
            val decoder = classDecoder(targetClass)
            if (decoder != null) {
                result = decoder.decode(obj, resolver)
            } else {
                gorgel.error("no decoder for $targetClass")
            }
        }
        return result
    }

    /**
     * A simple JSON object decoder for one-shot objects.  The given object is
     * by the [.decode] method, using the
     * [AlwaysBaseTypeResolver] to resolve type tags.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     * result will not necessarily be of this class, but will be assignable
     * to a variable of this class.
     * @param jsonObj  A JSON object describing the object to decode.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     * described by 'jsonObj', or null if the object could not be decoded
     * for some reason.
     */
    private fun decode(baseType: Class<*>, jsonObj: JsonObject): Any? = decode(baseType, jsonObj, AlwaysBaseTypeResolver)

    /**
     * A simple JSON string decoder for one-shot objects.  The given string is
     * first parsed, and then decoded as by the [ ][.decode] method, using the [ ] to resolve type tags.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     * result will not necessarily be of this class, but will be assignable
     * to a variable of this class.
     * @param str  A JSON string describing the object.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     * described by 'str', or null if the string was syntactically malformed
     * or the object could not be decoded for some reason.
     */
    fun decode(baseType: Class<*>, str: String): Any? {
        return try {
            val jsonObj = JsonParsing.jsonObjectFromString(str)!!
            decode(baseType, jsonObj)
        } catch (e: JsonParserException) {
            gorgel.warn("syntax error decoding object: ${e.message}")
            null
        }
    }
}
