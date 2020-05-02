package org.elkoserver.foundation.json

import org.elkoserver.foundation.json.ObjectDecoder.Companion.decode
import org.elkoserver.foundation.json.OptDouble
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.json.OptionalParameter
import org.elkoserver.foundation.json.OptionalParameter.Companion.missingValue
import org.elkoserver.json.JsonArray
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.AccessibleObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.time.Clock
import java.util.HashMap

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
internal abstract class Invoker(method: Member, private val myParamTypes: Array<Class<*>>, private val myParamNames: Array<out String>, firstIndex: Int, protected val traceFactory: TraceFactory, protected val clock: Clock) {
    /** Mapping of JSON parameter names to Java parameter positions  */
    private val myParamMap: MutableMap<String, Int>

    /** Parameter optionality flags, by position.  */
    private val myParamOptFlags: BooleanArray

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
    protected abstract fun invokeMe(target: Any?, params: Array<Any?>): Any?

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
    fun apply(target: Any?, firstParam: Any?, parameters: Set<Map.Entry<String, Any?>>, resolver: TypeResolver): Any? {
        val firstIndex = if (firstParam == null) 0 else 1
        val params = arrayOfNulls<Any>(myParamTypes.size)
        for ((paramName, value) in parameters) {
            val paramNum = myParamMap[paramName]
            if (paramNum == null) {
                if (paramName != "op" && paramName != "to" &&
                        paramName != "type" &&
                        paramName != "ref" && paramName != "_id") {
                    traceFactory.comm.warningm("ignored unknown parameter '$paramName'")
                }
                continue
            }
            val paramType = myParamTypes[paramNum]
            if (value != null) {
                val param = packParam(paramType, value, resolver)
                if (param == null) {
                    throw JSONInvocationException("parameter '$paramName' should be type $paramType")
                } else {
                    params[paramNum] = param
                }
            }
        }
        for (i in firstIndex until myParamTypes.size) {
            if (params[i] == null) {
                if (myParamOptFlags[i - firstIndex]) {
                    params[i] = null
                } else if (isOptionalParamType(myParamTypes[i])) {
                    params[i] = missingValue(myParamTypes[i])
                } else {
                    throw JSONInvocationException("expected parameter '${myParamNames[i - firstIndex]}' missing")
                }
            }
        }
        if (firstParam != null) {
            params[0] = firstParam
        }
        return try {
            invokeMe(target, params)
        } catch (e: IllegalAccessException) {
            throw JSONInvocationException("can't invoke method: $e")
        } catch (e: InvocationTargetException) {
            throw MessageHandlerException(
                    "exception in message handler method: ",
                    e.targetException)
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
    private fun isOptionalParamType(paramClass: Class<*>): Boolean {
        return OptionalParameter::class.java.isAssignableFrom(paramClass) ||
                paramClass.isArray
    }

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
            if (paramType == String::class.java) {
                value
            } else if (paramType == OptString::class.java) {
                OptString((value as String))
            } else {
                null
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
                (value as Double).toByte()
            } else if (paramType == java.lang.Short::class.java || paramType == Short::class.javaPrimitiveType || paramType == Short::class.java || paramType == Short::class) {
                (value as Double).toShort()
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
            if (paramType == JsonArray::class.java) {
                value
            } else if (paramType.isArray) {
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
            } else {
                null
            }
        } else if (valueType == JsonObject::class.java) {
            val actualValue = value as JsonObject
            if (paramType == JsonObject::class.java) {
                actualValue
            } else {
                decode(paramType, actualValue,
                        resolver, traceFactory, clock)
            }
        } else if (valueType == paramType) {
            value
        } else {
            null
        }
    }

    init {
        (method as AccessibleObject).isAccessible = true
        myParamMap = HashMap(myParamNames.size)
        myParamOptFlags = BooleanArray(myParamNames.size)
        for (i in myParamNames.indices) {
            var name = myParamNames[i]
            if (name[0] == '?') {
                name = name.substring(1)
                myParamOptFlags[i] = true
            } else {
                myParamOptFlags[i] = false
            }
            myParamMap[name] = i + firstIndex
        }
    }
}
