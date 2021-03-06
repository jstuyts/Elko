package org.elkoserver.foundation.json

import com.grack.nanojson.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel

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
        constructorInvokerCommGorgel: Gorgel,
        jsonToObjectDeserializer: JsonToObjectDeserializer,
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
    internal fun decode(obj: JsonObject, resolver: TypeResolver): Any? = myConstructor.construct(obj, resolver)

    init {
        val jsonConstructors = decodeClass.declaredConstructors.filter { it.getAnnotation(JsonMethod::class.java) != null }

        when {
            jsonConstructors.isEmpty() -> throw JsonSetupError("no JSON constructor for class ${decodeClass.name}")
            1 < jsonConstructors.size -> throw JsonSetupError("class ${decodeClass.name} has more than one JSON constructor")
        }

        val jsonConstructor = jsonConstructors.first()
        val paramTypes = jsonConstructor.parameterTypes
        val note = jsonConstructor.getAnnotation(JsonMethod::class.java)
        val paramNames = note.value
        val includeRawObject =
                when {
                    paramNames.size + 1 == paramTypes.size -> {
                        if (!JsonObject::class.java.isAssignableFrom(paramTypes[0])) {
                            throw JsonSetupError("class ${decodeClass.name} JSON constructor lacks a JsonObject first parameter")
                        }
                        true
                    }
                    paramNames.size != paramTypes.size -> throw JsonSetupError("class ${decodeClass.name} JSON constructor has wrong number of parameters")
                    else -> false
                }

        myConstructor = ConstructorInvoker(jsonConstructor, includeRawObject, jsonConstructor.parameterTypes, paramNames, constructorInvokerCommGorgel, jsonToObjectDeserializer, injectors)
    }
}
