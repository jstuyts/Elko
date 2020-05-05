package org.elkoserver.foundation.json

import com.grack.nanojson.JsonParserException
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonParsing
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.Constructor
import java.time.Clock

/**
 * A producer of some class of Java objects from JSON-encoded object
 * descriptors.
 *
 * Extract the appropriate constructor and associated descriptive
 * information from a class using the Java reflection API.
 *
 * The class must have a JSON-driven constructor.  Such constructors are
 * marked with the {@link JSONMethod} annotation.  The value of the
 * annotation is an array of Strings containing as many strings as the
 * constructor has parameters.  These strings will be the names of JSON
 * object descriptor properties and will be mapped one-to-one to the
 * corresponding parameters of the constructor itself.  Note that when the
 * constructor is invoked, any unmapped parameters (that is, properties
 * that exist in the JSON descriptor for the object but whose names do not
 * correspond to names in the JSONMethod annotation array) will be ignored.
 *
 * Alternatively, the constructor may have (exactly) one more parameter
 * than the number of Strings in the {@link JSONMethod} annotation, in
 * which case the one additional parameter must be of type {@link
 * JsonObject} and it must be the first parameter.  This first parameter
 * will be passed the value of the uninterpreted JSON object from which the
 * (other) parameters were extracted.
 *
 * If a constructor is annotated {@link JSONMethod} but does not follow
 * these rules, no decoder will be created for the class and an error
 * message will be logged.
 *
 * @param decodeClass  The Java class to construct a decoder for
 */
class ObjectDecoder private constructor(decodeClass: Class<*>, traceFactory: TraceFactory, clock: Clock) {
    /** Reflection information for the Java constructor this decoder invokes. */
    private val myConstructor: ConstructorInvoker

    /**
     * Invoke the constructor specified by a JSON object descriptor and return
     * the resulting Java object.
     *
     * @param obj  The JSON object descriptor to decode.
     * @param resolver  An object mapping JSON type tag strings to classes.
     *
     * @return the Java object described by 'obj', or null if 'obj' could not
     * be interpreted.
     */
    private fun decode(obj: JsonObject, resolver: TypeResolver): Any? {
        return myConstructor.construct(obj, resolver)
    }

    companion object {
        /** Mapping from Java class to the specific decoder for that class.  This
         * is a cache of decoders, to avoid recomputing reflection information.  */
        @Deprecated("Global variable")
        private val theDecoders: MutableMap<Class<*>, ObjectDecoder> = HashMap()

        /**
         * Obtain (by looking it up in theDecoders or by creating it) an
         * ObjectDecoder for a given class.
         *
         * @param decodeClass  The class whose decoder is sought
         *
         * @return a decoder for 'decodeClass', or null if one could not be made.
         */
        private fun classDecoder(decodeClass: Class<*>, traceFactory: TraceFactory, clock: Clock): ObjectDecoder? {
            var decoder = theDecoders[decodeClass]
            if (decoder == null) {
                try {
                    decoder = ObjectDecoder(decodeClass, traceFactory, clock)
                    theDecoders[decodeClass] = decoder
                } catch (e: JSONSetupError) {
                    traceFactory.comm.errorm(e.message ?: e.toString())
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
        fun decode(baseType: Class<*>, obj: JsonObject, resolver: TypeResolver, traceFactory: TraceFactory, clock: Clock): Any? {
            var result: Any? = null
            val typeName = obj.getString("type", null)
            val targetClass: Class<*>?
            if (typeName != null) {
                targetClass = resolver.resolveType(baseType, typeName)
                if (targetClass == null) {
                    traceFactory.comm.errorm("no Java class associated with JSON type tag '$typeName'")
                }
            } else {
                targetClass = baseType
            }
            if (targetClass != null) {
                val decoder = classDecoder(targetClass, traceFactory, clock)
                if (decoder != null) {
                    result = decoder.decode(obj, resolver)
                } else {
                    traceFactory.comm.errorm("no decoder for $targetClass")
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
        private fun decode(baseType: Class<*>, jsonObj: JsonObject, traceFactory: TraceFactory, clock: Clock): Any? {
            return decode(baseType, jsonObj, AlwaysBaseTypeResolver, traceFactory, clock)
        }

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
        fun decode(baseType: Class<*>, str: String, traceFactory: TraceFactory, clock: Clock): Any? {
            return try {
                val jsonObj = JsonParsing.jsonObjectFromString(str)!!
                decode(baseType, jsonObj, traceFactory, clock)
            } catch (e: JsonParserException) {
                traceFactory.comm.warningm("syntax error decoding object: ${e.message}")
                null
            }
        }
    }

    init {
        var jsonConstructor: Constructor<*>? = null
        var includeRawObject = false
        var paramNames: Array<out String>? = null
        for (constructor in decodeClass.declaredConstructors) {
            val note = constructor.getAnnotation(JSONMethod::class.java)
            if (note != null) {
                if (jsonConstructor != null) {
                    throw JSONSetupError("class ${decodeClass.name} has more than one JSON constructor")
                }
                val paramTypes = constructor.parameterTypes
                paramNames = note.value
                if (paramNames.size + 1 == paramTypes.size) {
                    if (!JsonObject::class.java.isAssignableFrom(paramTypes[0])) {
                        throw JSONSetupError("class ${decodeClass.name} JSON constructor lacks a JsonObject first parameter")
                    }
                    includeRawObject = true
                } else if (paramNames.size != paramTypes.size) {
                    throw JSONSetupError("class ${decodeClass.name} JSON constructor has wrong number of parameters")
                }
                jsonConstructor = constructor
            }
        }
        if (jsonConstructor == null) {
            throw JSONSetupError("no JSON constructor for class ${decodeClass.name}")
        }
        myConstructor = ConstructorInvoker(jsonConstructor, includeRawObject, jsonConstructor.parameterTypes, paramNames!!, traceFactory, clock)
    }
}