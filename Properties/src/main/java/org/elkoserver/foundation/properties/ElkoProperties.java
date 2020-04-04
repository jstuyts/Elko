package org.elkoserver.foundation.properties;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static java.util.Objects.hash;

/**
 * Enhanced version of the Java {@link Properties} class that knows how to pull
 * properties settings out of the command line and also provides a friendlier
 * interface to the values of the properties settings themselves.
 */
public class ElkoProperties
{
    private final Properties properties = new Properties();

    /**
     * Creates an empty property collection with no default values.
     */
    public ElkoProperties() {
        super();
    }

    public ElkoProperties(Map<String, String> properties) {
        this.properties.putAll(properties);
    }

    public boolean containsProperty(String property) {
        return properties.containsKey(property);
    }

    public String getProperty(String property) {
        return properties.getProperty(property);
    }

    public String getProperty(String property, String defaultValue) {
        return properties.getProperty(property, defaultValue);
    }

    public Iterable<String> stringPropertyNames() {
        return properties.stringPropertyNames();
    }

    /**
     * Get the value of a property as a boolean.  This will be true if the
     * property value is the string "true", false if the property value is
     * the string "false", and will be the default if the property value is
     * anything else or if the property is absent.
     *
     * @param property  The name of the property to test.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as a boolean.
     */
    public boolean boolProperty(String property, boolean defaultValue) {
        String val = properties.getProperty(property);
        if ("true".equals(val)) {
            return true;
        } else if ("false".equals(val)) {
            return false;
        } else {
            return defaultValue;
        }
    }

    /**
     * Get the value of a property as a double.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as a double.
     */
    public double doubleProperty(String property, double defaultValue) {
        String val = properties.getProperty(property);
        if (val != null) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    /**
     * Get the value of a property as an integer.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as an int.
     */
    public int intProperty(String property, int defaultValue) {
        String val = properties.getProperty(property);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    /**
     * Test the setting of a boolean property.  A boolean property is
     * <tt>true</tt> if its string value is "true", and <tt>false</tt> if its
     * string value is "false" or if the property is not set at all.
     *
     * @param property  The name of the property to test.
     *
     * @return the value of the property interpreted as a boolean.
     *
     * @throws IllegalArgumentException if the property has a value that is
     *    neither of the strings "true" nor "false".
     */
    public boolean testProperty(String property) {
        return boolProperty(property, false);
    }

    /**
     * Test if the value of a property is a particular string.
     *
     * @param property  The name of the property to test.
     * @param possibleValue  String value to test if it is equal to.
     *
     * @return <tt>true</tt> if the property has a value equal to
     *    <tt>possibleValue</tt>.
     */
    public boolean testProperty(String property, String possibleValue) {
        if (possibleValue != null) {
            return possibleValue.equals(properties.getProperty(property));
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object otherAsObject) {
        if (this == otherAsObject) return true;
        if (otherAsObject == null || getClass() != otherAsObject.getClass()) return false;
        ElkoProperties other = (ElkoProperties) otherAsObject;
        return properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return hash(properties);
    }
}
