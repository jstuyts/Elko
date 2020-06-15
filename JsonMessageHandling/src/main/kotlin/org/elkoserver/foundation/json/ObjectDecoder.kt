package org.elkoserver.foundation.json

import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.Constructor
import java.security.MessageDigest
import java.time.Clock
import java.util.Random

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
class ObjectDecoder internal constructor(
        decodeClass: Class<*>,
        traceFactory: TraceFactory,
        clock: Clock,
        jsonToObjectDeserializer: JsonToObjectDeserializer,
        random: Random,
        messageDigest: MessageDigest,
        injectors: Collection<Injector>) {
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
    internal fun decode(obj: JsonObject, resolver: TypeResolver?): Any? = myConstructor.construct(obj, resolver)

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
        myConstructor = ConstructorInvoker(jsonConstructor, includeRawObject, jsonConstructor.parameterTypes, paramNames!!, traceFactory, clock, jsonToObjectDeserializer, random, messageDigest, injectors)
    }
}
