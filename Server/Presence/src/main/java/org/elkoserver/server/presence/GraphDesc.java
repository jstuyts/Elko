package org.elkoserver.server.presence;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JsonObject;

import java.lang.reflect.InvocationTargetException;

class GraphDesc {
    private String myClassName;
    private String myGraphName;
    private JsonObject myConf;

    @JSONMethod({ "class", "name", "?conf" })
    GraphDesc(String className, String graphName, JsonObject conf) {
        myClassName = className;
        myGraphName = graphName;
        if (conf == null) {
            conf = new JsonObject();
        }
        myConf = conf;
    }

    SocialGraph init(PresenceServer master) {
        try {
            SocialGraph newGraph =
                (SocialGraph) Class.forName(myClassName).getConstructor().newInstance();
            newGraph.init(master, new Domain(myGraphName), myConf);
            return newGraph;
        } catch (ClassNotFoundException e) {
            master.appTrace().errori("class " + myClassName + " not found");
            return null;
        } catch (InstantiationException | IllegalAccessException e) {
            master.appTrace().errorm("unable to instantiate " + myClassName +
                                     ": " + e);
            return null;
        } catch (NoSuchMethodException e) {
            master.appTrace().errorm("class " + myClassName +
                    " does not have a public no-arg constructor: " + e);
            return null;
        } catch (InvocationTargetException e) {
            master.appTrace().errorm("error occurred during instantiation of " + myClassName +
                    ": " + e.getCause());
            return null;
        }
    }
}
