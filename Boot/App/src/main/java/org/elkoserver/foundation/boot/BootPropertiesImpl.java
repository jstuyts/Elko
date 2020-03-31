package org.elkoserver.foundation.boot;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Enhanced version of the Java {@link Properties} class that knows how to pull
 * properties settings out of the command line and also provides a friendlier
 * interface to the values of the properties settings themselves.
 */
public class BootPropertiesImpl extends BootProperties
{
    /**
     * Creates an empty property collection with no default values.
     */
    public BootPropertiesImpl() {
        super();
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
            properties.put(key, value);
        }

        /* Return the actual args that remain */
        return args.toArray(new String[0]);
    }
}
