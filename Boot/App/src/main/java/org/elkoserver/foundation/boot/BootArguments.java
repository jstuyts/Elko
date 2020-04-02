package org.elkoserver.foundation.boot;

import org.elkoserver.foundation.properties.ElkoProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BootArguments {

    final String mainClassName;
    final ElkoProperties bootProperties;

    BootArguments(String[] args) {
        super();

        List<String> remainingArgs = new ArrayList<>(args.length);
        Map<String, String> props = new HashMap<>(args.length);
        scanArgsForProperties(args, props, remainingArgs);

        if (remainingArgs.size() < 1) {
            throw new Error("Boot needs class name to boot");
        }

        mainClassName = remainingArgs.get(0);
        bootProperties = new ElkoProperties(props);
    }

    private void scanArgsForProperties(String[] args, Map<String, String> props, List<String> remainingArgs) {
        for (String arg : args) {
            int j = arg.indexOf('=');
            if (arg.indexOf('=') > 0) {
                String key = arg.substring(0, j);
                String value = arg.substring(j + 1);
                props.put(key, value);
            } else {
                remainingArgs.add(arg);
            }
        }
    }
}
