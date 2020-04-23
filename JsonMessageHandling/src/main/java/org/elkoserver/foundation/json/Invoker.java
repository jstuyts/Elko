package org.elkoserver.foundation.json;

import org.elkoserver.json.JsonArray;
import org.elkoserver.json.JsonObject;
import org.elkoserver.json.JsonWrapping;
import org.elkoserver.util.trace.TraceFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elkoserver.json.JsonWrapping.wrapWithElkoJsonImplementationIfNeeded;

/**
 * Precomputed Java reflection information needed invoke a method or
 * constructor via a JSON message or JSON-encoded object descriptor.
 */
abstract class Invoker {
    /** Mapping of JSON parameter names to Java parameter positions */
    private Map<String, Integer> myParamMap;

    /** Parameter types, by position. */
    private Class<?>[] myParamTypes;

    /** Parameter names, by position. */
    private String[] myParamNames;

    protected final TraceFactory traceFactory;

    protected final Clock clock;

    /** Parameter optionality flags, by position. */
    private boolean[] myParamOptFlags;

    /**
     * Constructor.
     *
     * @param method The Java reflection API method or constructor object for
     *    the method that this invoker will invoke.  This is typed as Member
     *    only because Member is the common parent interface of Constructor and
     *    Method; the parameter actually passed must be a Constructor or Method
     *    object, not some other implementor of Member.
     * @param paramTypes  Array of classes of the parameters of 'method'.
     * @param paramNames  JSON names for the parameters.
     * @param firstIndex  Index of first JSON parameter.
     *
     * @throws JSONSetupError if the method breaks the rules for a JSON method.
     */
    Invoker(Member method, Class<?>[] paramTypes, String[] paramNames,
            int firstIndex, TraceFactory traceFactory, Clock clock)
    {
        myParamTypes = paramTypes;
        myParamNames = paramNames;
        this.traceFactory = traceFactory;
        this.clock = clock;

        ((AccessibleObject) method).setAccessible(true);

        myParamMap = new HashMap<>(myParamNames.length);
        myParamOptFlags = new boolean[myParamNames.length];
        for (int i = 0; i < myParamNames.length; ++i) {
            String name = myParamNames[i];
            if (name.charAt(0) == '?') {
                name = name.substring(1);
                myParamOptFlags[i] = true;
            } else {
                myParamOptFlags[i] = false;
            }
            myParamMap.put(name, i + firstIndex);
        }
    }

    /**
     * Subclass-provided method that knows how to actually call the method.
     *
     * In the Java reflection API, methods and constructors are represented by
     * different classes with different interfaces (even though they are really
     * the same thing underneath).
     *
     * @param target  Object whose method is being invoked (null in the case of
     *    a constructor).
     * @param params  Parameters to pass to the method.
     *
     * @return whatever the invoked method returns.
     */
    protected abstract Object invokeMe(Object target, Object[] params)
        throws IllegalAccessException, InvocationTargetException;

    /**
     * Invoke the method or constructor held by this Invoker.
     *
     * @param target  Object the method is to be invoked on (null if it's a
     *    constructor call)
     * @param firstParam  First parameter value to pass to the method, if not
     *    null.
     * @param parameters  Set of the remaining parameter name/value pairs.
     * @param resolver  An object mapping JSON type tag strings to classes.
     *
     * @return the result returned by the method or constructor
     *
     * @throws MessageHandlerException if there was a problem in the execution
     *    of the invoked method.
     */
    Object apply(Object target, Object firstParam,
                 Set<Map.Entry<String, Object>> parameters,
                 TypeResolver resolver)
        throws MessageHandlerException, JSONInvocationException
    {
        int firstIndex = firstParam == null ? 0 : 1;
        Object[] params = new Object[myParamTypes.length];
        for (Map.Entry<String, Object> entry : parameters) {
            String paramName = entry.getKey();
            Integer paramNum = myParamMap.get(paramName);
            if (paramNum == null) {
                if (!paramName.equals("op") && !paramName.equals("to") &&
                        !paramName.equals("type") &&
                        !paramName.equals("ref") && !paramName.equals("_id")) {
                    traceFactory.comm.warningm("ignored unknown parameter '" +
                                        paramName + "'");
                }
                continue;
            }
            Class<?> paramType = myParamTypes[paramNum];
            Object value = entry.getValue();
            if (value != null) {
                Object param = packParam(paramType, value, resolver);
                if (param == null) {
                    throw new JSONInvocationException(
                        "parameter '" + paramName + "' should be type " +
                        paramType);
                } else {
                    params[paramNum] = param;
                }
            }
        }
        for (int i = firstIndex; i < myParamTypes.length; ++i) {
            if (params[i] == null) {
                if (myParamOptFlags[i-firstIndex]) {
                    params[i] = null;
                } else if (isOptionalParamType(myParamTypes[i])) {
                    params[i] =
                        OptionalParameter.missingValue(myParamTypes[i]);
                } else {
                    throw new JSONInvocationException("expected parameter '" +
                        myParamNames[i-firstIndex] + "' missing");
                }
            }
        }
        if (firstParam != null) {
            params[0] = firstParam;
        }
        try {
            return invokeMe(target, params);
        } catch (IllegalAccessException e) {
            throw new JSONInvocationException("can't invoke method: " + e);
        } catch (InvocationTargetException e) {
            throw new MessageHandlerException(
                "exception in message handler method: ",
                e.getTargetException());
        }
    }

    /**
     * Check if a class is an optional parameter type.
     *
     * @param paramClass  The class to be tested.
     *
     * @return true if paramClass is one of the supported optional parameter
     *    classes.
     */
    private boolean isOptionalParamType(Class<?> paramClass) {
        return OptionalParameter.class.isAssignableFrom(paramClass) ||
            paramClass.isArray();
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
     *    value is of the wrong type.
     */
    private Object packParam(Class<?> paramType, Object value,
                             TypeResolver resolver) {
        Class<?> valueType = value.getClass();
        if (valueType == String.class) {
            if (paramType == String.class) {
                return value;
            } else if (paramType == OptString.class) {
                return new OptString((String) value);
            } else {
                return null;
            }
        } else if (valueType == Long.class) {
            if (paramType == long.class || paramType == Long.class) {
                return value;
            } else if (paramType == int.class || paramType == Integer.class) {
                return ((Long) value).intValue();
            } else if (paramType == OptInteger.class) {
                return new OptInteger(((Long) value).intValue());
            } else if (paramType == byte.class || paramType == Byte.class) {
                return ((Long) value).byteValue();
            } else if (paramType == short.class || paramType == Short.class) {
                return ((Long) value).shortValue();
            } else if (paramType == double.class ||
                       paramType == Double.class) {
                return ((Long) value).doubleValue();
            } else if (paramType == float.class || paramType == Float.class) {
                return ((Long) value).floatValue();
            } else if (paramType == OptDouble.class) {
                return new OptDouble(((Long) value).doubleValue());
            } else {
                return null;
            }
        } else if (valueType == Integer.class) {
            if (paramType == long.class || paramType == Long.class) {
                return ((Integer)value).longValue();
            } else if (paramType == int.class || paramType == Integer.class) {
                return value;
            } else if (paramType == OptInteger.class) {
                return new OptInteger((Integer) value);
            } else if (paramType == byte.class || paramType == Byte.class) {
                return ((Integer) value).byteValue();
            } else if (paramType == short.class || paramType == Short.class) {
                return ((Integer) value).shortValue();
            } else if (paramType == double.class ||
                       paramType == Double.class) {
                return ((Integer) value).doubleValue();
            } else if (paramType == float.class || paramType == Float.class) {
                return ((Integer) value).floatValue();
            } else if (paramType == OptDouble.class) {
                return new OptDouble(((Integer) value).doubleValue());
            } else {
                return null;
            }
        } else if (valueType == Double.class) {
            if (paramType == double.class || paramType == Double.class) {
                return value;
            } else if (paramType == float.class || paramType == Float.class) {
                return value;
            } else if (paramType == OptDouble.class) {
                return new OptDouble((Double) value);
            } else if (paramType == long.class || paramType == Long.class) {
                return ((Double) value).longValue();
            } else if (paramType == int.class || paramType == Integer.class) {
                return ((Double) value).intValue();
            } else if (paramType == OptInteger.class) {
                return new OptInteger(((Double) value).intValue());
            } else if (paramType == byte.class || paramType == Byte.class) {
                return ((Double) value).byteValue();
            } else if (paramType == short.class || paramType == Short.class) {
                return ((Double) value).shortValue();
            } else {
                return null;
            }
        } else if (valueType == Boolean.class) {
            if (paramType == boolean.class || paramType == Boolean.class) {
                return value;
            } else if (paramType == OptBoolean.class) {
                return new OptBoolean((Boolean) value);
            } else {
                return null;
            }
        } else if (valueType == JsonArray.class) {
            JsonArray arrayValue = (JsonArray) value;
            if (paramType == JsonArray.class) {
                return value;
            } else if (paramType.isArray()) {
                Class<?> baseType = paramType.getComponentType();
                Object[] valueArray = arrayValue.toArray();
                Object result = Array.newInstance(baseType, valueArray.length);
                for (int i = 0; i < valueArray.length; ++i) {
                    Object elemValue =
                        packParam(baseType, valueArray[i], resolver);
                    if (elemValue != null) {
                        Array.set(result, i, elemValue);
                    } else {
                        return null;
                    }
                }
                return result;
            } else {
                return null;
            }
        } else if (valueType == JsonObject.class) {
            JsonObject actualValue = (JsonObject) value;
            if (paramType == JsonObject.class) {
                return actualValue;
            } else {
                return ObjectDecoder.decode(paramType, actualValue,
                                            resolver, traceFactory, clock);
            }
        } else if (valueType == paramType) {
            return value;
        } else {
            return null;
        }
    }
}
