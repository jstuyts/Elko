package org.elkoserver.foundation.boot;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Enhanced version of the Java {@link Properties} class that knows how to pull
 * properties settings out of the command line and also provides a friendlier
 * interface to the values of the properties settings themselves.
 */
public class BootProperties extends Properties
{
    /**
     * Creates an empty property collection with no default values.
     */
    public BootProperties() {
        super();
    }

    /**
     * Creates an empty property collection with the specified defaults.
     *
     * @param defaults  A set of properties to use as a default initializer.
     */
    public BootProperties(Properties defaults) {
        super(defaults);
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
        String val = getProperty(property);
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
        String val = getProperty(property);
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
        String val = getProperty(property);
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
     * Read properties files and parse property settings from the command line.
     * Unless otherwise directed, a default properties file is read for
     * property definitions.  The command line is parsed for additional
     * property assignments according to the following rules:<p>
     *
     * <tt><i>key</i>=<i>val</i></tt><blockquote>
     *      The property named <tt><i>key</i></tt> is given the value
     *      <tt><i>val</i></tt>.</blockquote>
     *
     * <tt><i>anythingelse</i></tt><blockquote>
     *      The argument is added to the returned arguments array.</blockquote>
     *
     * @param inArgs  The raw, unprocessed command line arguments array.
     *
     * @return an array of the arguments remaining after all the
     *    property-specifying ones are stripped out.
     */
    public String[] scanArgs(String[] inArgs) {
        List<String> args = new ArrayList<>(inArgs.length);
        List<String> propSets = new ArrayList<>(inArgs.length);

        /* First pass parse of inArgs array */
        for (String arg : inArgs) {
            if (arg.indexOf('=') > 0) {
                propSets.add(arg);
            } else {
                args.add(arg);
            }
        }

        /* Assign props set directly on command line */
        for (String assoc : propSets) {
            int j = assoc.indexOf('=');
            String key = assoc.substring(0, j);
            String value = assoc.substring(j + 1);
            put(key, value);
        }

        /* Return the actual args that remain */
        return args.toArray(new String[0]);
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
            return possibleValue.equals(getProperty(property));
        } else {
            return false;
        }
    }
}
