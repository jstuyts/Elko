package org.elkoserver.foundation.json

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.json.OptionalParameter.Companion.missingValue
import org.elkoserver.util.trace.slf4j.Gorgel
import java.lang.reflect.AccessibleObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member

/**
 * Precomputed Java reflection information needed invoke a method or
 * constructor via a JSON message or JSON-encoded object descriptor.
 *
 * @param method The Java reflection API method or constructor object for
 *    the method that this invoker will invoke.  This is typed as Member
 *    only because Member is the common parent interface of Constructor and
 *    Method; the parameter actually passed must be a Constructor or Method
 *    object, not some other implementor of Member.
 * @param myParamTypes  Array of classes of the parameters of 'method'.
 * @param myParamNames  JSON names for the parameters.
 * @param firstIndex  Index of first JSON parameter.
 */
abstract class Invoker<in TTarget>(
        method: Member,
        private val myParamTypes: Array<Class<*>>,
        private val myParamNames: Array<out String>,
        firstIndex: Int,
        protected val commGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer) {
    /** Mapping of JSON parameter names to Java parameter positions  */
    private val myParamMap: MutableMap<String, Int> = HashMap(myParamNames.size)

    /** Parameter optionality flags, by position.  */
    private val myParamOptFlags = BooleanArray(myParamNames.size)

    /**
     * Subclass-provided method that knows how to actually call the method.
     *
     * In the Java reflection API, methods and constructors are represented by
     * different classes with different interfaces (even though they are really
     * the same thing underneath).
     *
     * @param target  Object whose method is being invoked (null in the case of
     * a constructor).
     * @param params  Parameters to pass to the method.
     *
     * @return whatever the invoked method returns.
     */
    protected abstract fun invokeMe(target: TTarget, params: Array<Any?>): Any?

    /**
     * Invoke the method or constructor held by this Invoker.
     *
     * @param target  Object the method is to be invoked on (null if it's a
     * constructor call)
     * @param firstParam  First parameter value to pass to the method, if not
     * null.
     * @param parameters  Set of the remaining parameter name/value pairs.
     * @param resolver  An object mapping JSON type tag strings to classes.
     *
     * @return the result returned by the method or constructor
     *
     * @throws MessageHandlerException if there was a problem in the execution
     * of the invoked method.
     */
    fun apply(target: TTarget, firstParam: Any?, parameters: Set<Map.Entry<String, Any?>>, resolver: TypeResolver): Any? {
        val firstIndex = if (firstParam == null) 0 else 1
        val params = arrayOfNulls<Any>(myParamTypes.size)
        for ((paramName, value) in parameters) {
            val paramNum = myParamMap[paramName]
            if (paramNum == null) {
                if (paramName != "op" && paramName != "to" &&
                        paramName != "type" &&
                        paramName != "ref" && paramName != "_id") {
                    commGorgel.warn("ignored unknown parameter '$paramName'")
                }
            } else {
                val paramType = myParamTypes[paramNum]
                if (value != null) {
                    val param = packParam(paramType, value, resolver)
                            ?: throw JsonInvocationException("parameter '$paramName' should be type $paramType")
                    params[paramNum] = param
                }
            }
        }
        (firstIndex until myParamTypes.size)
                .filter { params[it] == null }
                .forEach {
                    when {
                        myParamOptFlags[it - firstIndex] -> params[it] = null
                        isOptionalParamType(myParamTypes[it]) -> params[it] = missingValue(myParamTypes[it])
                        else -> throw JsonInvocationException("expected parameter '${myParamNames[it - firstIndex]}' missing")
                    }
                }
        if (firstParam != null) {
            params[0] = firstParam
        }
        return try {
            invokeMe(target, params)
        } catch (e: IllegalAccessException) {
            throw JsonInvocationException("can't invoke method: $e")
        } catch (e: InvocationTargetException) {
            throw MessageHandlerException("exception in message handler method: ", e.targetException)
        }
    }

    /**
     * Check if a class is an optional parameter type.
     *
     * @param paramClass  The class to be tested.
     *
     * @return true if paramClass is one of the supported optional parameter
     * classes.
     */
    private fun isOptionalParamType(paramClass: Class<*>) =
            OptionalParameter::class.java.isAssignableFrom(paramClass) ||
                    paramClass.isArray

    /**
     * Produce the object that should actually be passed to the method or
     * constructor for a particular parameter when invoked through the Java
     * reflection API.  This may be a different object than appeared in the
     * corresponding name parameter from the decoded JSON message or object
     * descriptor, due to the numeric type coercion, JSON object literal
     * interpretation, JSON array interpretation, and optional parameter types.
     *
     * @param paramType  The type that the method is expecting.
     * @param value  The object value from the JSON message.
     * @param resolver  Type resolver for object parameters.
     *
     * @return the object to pass to the method for 'value', or null if the
     * value is of the wrong type.
     */
    private fun packParam(paramType: Class<*>, value: Any, resolver: TypeResolver): Any? {
        val valueType: Class<*> = value.javaClass
        return if (valueType == String::class.java) {
            when (paramType) {
                String::class.java -> value
                OptString::class.java -> OptString((value as String))
                else -> null
            }
        } else if (valueType == java.lang.Long::class.java) {
            if (paramType == java.lang.Long::class.java || paramType == Long::class.javaPrimitiveType || paramType == Long::class.java || paramType == Long::class) {
                value
            } else if (paramType == Integer::class.java || paramType == Int::class.javaPrimitiveType || paramType == Int::class.java || paramType == Int::class) {
                (value as Long).toInt()
            } else if (paramType == OptInteger::class.java) {
                OptInteger((value as Long).toInt())
            } else if (paramType == java.lang.Byte::class.java || paramType == Byte::class.javaPrimitiveType || paramType == Byte::class.java || paramType == Byte::class) {
                (value as Long).toByte()
            } else if (paramType == java.lang.Short::class.java || paramType == Short::class.javaPrimitiveType || paramType == Short::class.java || paramType == Short::class) {
                (value as Long).toShort()
            } else if (paramType == java.lang.Double::class.java || paramType == Double::class.javaPrimitiveType || paramType == Double::class.java || paramType == Double::class) {
                (value as Long).toDouble()
            } else if (paramType == java.lang.Float::class.java || paramType == Float::class.javaPrimitiveType || paramType == Float::class.java || paramType == Float::class) {
                (value as Long).toFloat()
            } else if (paramType == OptDouble::class.java) {
                OptDouble((value as Long).toDouble())
            } else {
                null
            }
        } else if (valueType == Integer::class.java) {
            if (paramType == java.lang.Long::class.java || paramType == Long::class.javaPrimitiveType || paramType == Long::class.java || paramType == Long::class) {
                (value as Int).toLong()
            } else if (paramType == Integer::class.java || paramType == Int::class.javaPrimitiveType || paramType == Int::class.java || paramType == Int::class) {
                value
            } else if (paramType == OptInteger::class.java) {
                OptInteger((value as Int))
            } else if (paramType == java.lang.Byte::class.java || paramType == Byte::class.javaPrimitiveType || paramType == Byte::class.java || paramType == Byte::class) {
                (value as Int).toByte()
            } else if (paramType == java.lang.Short::class.java || paramType == Short::class.javaPrimitiveType || paramType == Short::class.java || paramType == Short::class) {
                (value as Int).toShort()
            } else if (paramType == java.lang.Double::class.java || paramType == Double::class.javaPrimitiveType || paramType == Double::class.java || paramType == Double::class) {
                (value as Int).toDouble()
            } else if (paramType == java.lang.Float::class.java || paramType == Float::class.javaPrimitiveType || paramType == Float::class.java || paramType == Float::class) {
                (value as Int).toFloat()
            } else if (paramType == OptDouble::class.java) {
                OptDouble((value as Int).toDouble())
            } else {
                null
            }
        } else if (valueType == java.lang.Double::class.java) {
            if (paramType == java.lang.Double::class.java || paramType == Double::class.javaPrimitiveType || paramType == Double::class.java || paramType == Double::class) {
                value
            } else if (paramType == java.lang.Float::class.java || paramType == Float::class.javaPrimitiveType || paramType == Float::class.java || paramType == Float::class) {
                value
            } else if (paramType == OptDouble::class.java) {
                OptDouble((value as Double))
            } else if (paramType == java.lang.Long::class.java || paramType == Long::class.javaPrimitiveType || paramType == Long::class.java || paramType == Long::class) {
                (value as Double).toLong()
            } else if (paramType == Integer::class.java || paramType == Int::class.javaPrimitiveType || paramType == Int::class.java || paramType == Int::class) {
                (value as Double).toInt()
            } else if (paramType == OptInteger::class.java) {
                OptInteger((value as Double).toInt())
            } else if (paramType == java.lang.Byte::class.java || paramType == Byte::class.javaPrimitiveType || paramType == Byte::class.java || paramType == Byte::class) {
                (value as Double).toInt().toByte()
            } else if (paramType == java.lang.Short::class.java || paramType == Short::class.javaPrimitiveType || paramType == Short::class.java || paramType == Short::class) {
                (value as Double).toInt().toShort()
            } else {
                null
            }
        } else if (valueType == java.lang.Boolean::class.java) {
            if (paramType == java.lang.Boolean::class.java || paramType == Boolean::class.javaPrimitiveType || paramType == Boolean::class.java || paramType == Boolean::class) {
                value
            } else if (paramType == OptBoolean::class.java) {
                OptBoolean((value as Boolean))
            } else {
                null
            }
        } else if (valueType == JsonArray::class.java) {
            val arrayValue = value as JsonArray
            when {
                paramType == JsonArray::class.java -> value
                paramType.isArray -> {
                    val baseType = paramType.componentType
                    val valueArray = arrayValue.toArray()
                    val result = java.lang.reflect.Array.newInstance(baseType, valueArray.size)
                    for (i in valueArray.indices) {
                        val elemValue = packParam(baseType, valueArray[i], resolver)
                        if (elemValue != null) {
                            java.lang.reflect.Array.set(result, i, elemValue)
                        } else {
                            return null
                        }
                    }
                    result
                }
                else -> null
            }
        } else if (valueType == JsonObject::class.java) {
            val actualValue = value as JsonObject
            if (paramType == JsonObject::class.java) {
                actualValue
            } else {
                jsonToObjectDeserializer.decode(paramType, actualValue, resolver)
            }
        } else if (valueType == paramType) {
            value
        } else {
            null
        }
    }

    init {
        (method as AccessibleObject).isAccessible = true
        myParamNames.forEachIndexed { i, name ->
            val actualName: String
            if (name[0] == '?') {
                actualName = name.substring(1)
                myParamOptFlags[i] = true
            } else {
                actualName = name
                myParamOptFlags[i] = false
            }
            myParamMap[actualName] = i + firstIndex
        }
    }
}
