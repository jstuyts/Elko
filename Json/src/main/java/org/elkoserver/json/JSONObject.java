package org.elkoserver.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A parsed JSON object.
 *
 * This class represents a JSON object that has been received or is being
 * constructed.  It provides random access to the properties of the object.
 */
public class JSONObject {
    /** Table where the object's properties are kept.  Maps from property names
        to property values. */
    private final Map<String, Object> myProperties;

    /**
     * Construct a new, empty JSON object.
     */
    public JSONObject() {
        myProperties = new HashMap<>();
    }

    /**
     * Construct a JSON object from a pre-existing map.
     *
     * @param map A map mapping JSON property names to their values.  All the
     *   keys in this map must be strings and the values must be {@link
     *   Boolean}, {@link Double}, {@link Long}, {@link String}, {@link
     *   JSONArray}, or {@link JSONObject}.
     */
    public JSONObject(Map<String, Object> map) {
        myProperties = new HashMap<>(map);
    }

    /**
     * Construct a JSON object by copying another pre-existing JSON object.
     *
     * @param original  The original JSON object to be copied.
     */
    public JSONObject(JSONObject original) {
        myProperties = new HashMap<>(original.myProperties);
    }

    /**
     * Construct a JSON object representing a typed struct.
     *
     * @param type  The type name (this will be added as the property
     *    'type:').
     */
    public JSONObject(String type) {
        this();
        addProperty("type", type);
    }

    /**
     * Construct a JSON object representing a message.
     *
     * @param target  The reference ID of the message target (this will be
     *    added as the property 'to:').
     * @param verb  The message verb (this will be added as the property
     *    'op:').
     */
    public JSONObject(String target, String verb) {
        this();
        addProperty("to", target);
        addProperty("op", verb);
    }

    /**
     * Add a property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its value.
     *
     * Although class declaration rules of Java compel 'value' to be declared
     * as class {@link Object}, in reality it must be null or one of the
     * classes: {@link Boolean}, {@link Double}, {@link Long}, {@link String},
     * {@link JSONArray}, {@link JSONObject}.
     */
    public void addProperty(String name, Object value) {
        myProperties.put(name, value);
    }

    /**
     * Obtain an integer property value.
     *
     * @param name  The name of the integer property sought.
     *
     * @return  The int value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a number.
     */
    public int getInt(String name) throws JSONDecodingException{
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj.intValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the value of a property.
     *
     * @param name  The name of the property sought
     *
     * @return  An object representing the value of the property named by
     *    'name', or null if the object has no such property.
     */
    public Object getProperty(String name) {
        return myProperties.get(name);
    }

    /**
     * Obtain a JSON object property value.
     *
     * @param name  The name of the object property sought.
     *
     * @return  The JSON object value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a JSON object.
     */
    public JSONObject getObject(String name) throws JSONDecodingException {
        try {
            JSONObject obj = (JSONObject) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain a string property value.
     *
     * @param name  The name of the string property sought.
     *
     * @return  The string value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' if
     *    the value is not a string.
     */
    public String getString(String name) throws JSONDecodingException {
        try {
            String obj = (String) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a string value as was expected");
        }
    }

    /**
     * Obtain the boolean value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the boolean property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The boolean value of the property named by 'name' or
     *    'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not boolean.
     */
    public boolean optBoolean(String name, boolean defaultValue)
        throws JSONDecodingException
    {
        try {
            Boolean obj = (Boolean) myProperties.get(name);
            return obj == null ? defaultValue : obj;
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a boolean value as was expected");
        }
    }

    /**
     * Obtain the double value of a property or a default value if the property
     * has no value.
     *
     * @param name  The name of the double property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The double value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public double optDouble(String name, double defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.doubleValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a floating-point numeric value as was expected");
        }
    }

    /**
     * Obtain the integer value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the integer property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The int value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public int optInt(String name, int defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.intValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the long value of a property or a default value if the property
     * has no value.
     *
     * @param name  The name of the long property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The long value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public long optLong(String name, long defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.longValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the JSON object value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the JSON object property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The JSON object value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a {@link JSONObject}.
     */
    public JSONObject optObject(String name, JSONObject defaultValue)
        throws JSONDecodingException
    {
        try {
            JSONObject obj = (JSONObject) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain the string value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the string property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The String value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a {@link String}.
     */
    public String optString(String name, String defaultValue)
        throws JSONDecodingException
    {
        try {
            String obj = (String) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a string value as was expected");
        }
    }

    /**
     * Get a set view of the properties of this JSON object.
     *
     * @return a set of this object's properties.
     */
    public Set<Map.Entry<String, Object>> properties() {
        return myProperties.entrySet();
    }

    /**
     * Remove a property from this JSON object.
     *
     * @param name  The name of the property to remove.
     *
     * @return the value of the property that was removed or null if there was
     *    no such property to remove.
     */
    public Object remove(String name) {
        return myProperties.remove(name);
    }

    /**
     * Return the number of properties in this JSON object.
     *
     * @return the number properties in this JSON object.
     */
    public int size() {
        return myProperties.size();
    }

    /**
     * Interpreting this JSON object as a JSON message, obtain its target.
     *
     * @return the string value of the 'to' property if it has one, or null.
     */
    public String target() {
        return weakStringProperty("to");
    }

    /**
     * Obtain a printable string representation of this object.
     *
     * @return a printable representation of this object.
     */
    public String toString() {
        return JsonObjectSerialization.literal(this, EncodeControl.forRepository).sendableString();
    }

    /**
     * Interpreting this JSON object as an encoded object descriptor, obtain
     * its type name.
     *
     * @return the string value of the 'type' property if it has one, or null.
     */
    public String type() {
        return weakStringProperty("type");
    }

    /**
     * Interpreting this JSON object as a JSON message, obtain its verb.
     *
     * @return the string value of the 'op' property if it has one, or null.
     */
    public String verb() {
        return weakStringProperty("op");
    }

    /**
     * Obtain the string value of a property or null if the property has no
     * value or is not a string.
     *
     * @param name  The name of the property sought.
     *
     * @return  The String value of the property named by 'name', if it exists
     *   and is a string, else null.
     */
    private String weakStringProperty(String name) {
        Object weakProperty = myProperties.get(name);
        if (weakProperty instanceof String) {
            return (String) weakProperty;
        } else {
            return null;
        }
    }
}
