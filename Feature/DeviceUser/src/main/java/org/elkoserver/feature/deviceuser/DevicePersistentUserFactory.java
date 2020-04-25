package org.elkoserver.feature.deviceuser;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JsonObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserFactory;
import org.elkoserver.util.trace.Trace;

import java.util.function.Consumer;

/**
 * Factory that generates a persistent user object from connected mobile device
 * information.
 */
public class DevicePersistentUserFactory implements UserFactory {
    
    /**
     * The name of the device (IOS, etc)
     */
    private String myDevice;

    /**
     * JSON-driven constructor.
     *
     * @param device  The name of the device (IOS, etc).
     */
    @JSONMethod({ "device" })
    DevicePersistentUserFactory(String device) {
        myDevice = device;
    }  
    
    /**
     * Obtain the name of the device this factory works with.
     *
     * @return this factory's device string.
     */
    public String getDevice() {
        return myDevice;
    }

    /**
     * Produce a user object.
     *
     * @param contextor The contextor of the server in which the requested
     *    user will be present
     * @param connection  The connection over which the new user presented
     *    themselves.
     * @param param   Arbitrary JSON object parameterizing the construction.
     *    this is analogous to the user record read from the ODB, but may be
     *    anything that makes sense for the particular factory implementation.
     *    Of course, the sender of this parameter must be coordinated with the
     *    factory implementation.
     * @param handler   Handler to be called with the result.  The result will
     *    be the user object that was produced, or null if none could be.
     */
    public void provideUser(final Contextor contextor, Connection connection,
                            JsonObject param, final Consumer<Object> handler) {
        final DeviceCredentials creds =
            extractCredentials(contextor.appTrace(), param);
        if (creds != null) {
            contextor.server().enqueueSlowTask(() -> {
                contextor.queryObjects(deviceQuery(creds.uuid), null, 0,
                        new DeviceQueryResultHandler(contextor, creds, handler));
                return null;
            }, null);
        } else {
            handler.accept(null);
        }
    }

    private static class DeviceQueryResultHandler implements Consumer<Object> {
        private Contextor myContextor;
        private DeviceCredentials myCreds;
        private Consumer<Object> myHandler;

        DeviceQueryResultHandler(Contextor contextor, DeviceCredentials creds,
                                 Consumer<Object> handler)
        {
            myContextor = contextor;
            myCreds = creds;
            myHandler = handler;
        }

        public void accept(Object queryResult) {
            User user;
            Object[] result = (Object []) queryResult;
            if (result != null && result.length > 0) {
                if (result.length > 1) {
                    myContextor.appTrace().warningm("uuid query loaded " +
                        result.length + " users, choosing first");
                }
                user = (User) result[0];
            } else {
                String name = myCreds.name;
                if (name == null) {
                    name = "AnonUser";
                }
                String uuid = myCreds.uuid;
                myContextor.appTrace().eventi("synthesizing user record for " +
                                              uuid);
                DeviceUserMod mod = new DeviceUserMod(uuid);
                user = new User(name, new Mod[] { mod }, null,
                                myContextor.uniqueID("u"));
                user.markAsChanged();
            }
            myHandler.accept(user);
        }
    }

    private JsonObject deviceQuery(String uuid) {
        // { type: "user",
        //   mods: { $elemMatch: { type: "deviceuser", uuid: UUID }}}

        JsonObject modMatchPattern = new JsonObject();
        modMatchPattern.put("type", "deviceuser");
        modMatchPattern.put("uuid", uuid);

        JsonObject modMatch = new JsonObject();
        modMatch.put("$elemMatch", modMatchPattern);

        JsonObject queryTemplate = new JsonObject();
        queryTemplate.put("type", "user");
        queryTemplate.put("mods", modMatch);

        return queryTemplate;
    }
    
    /**
     * Extract the user login credentials from a user factory parameter object.
     *
     * @param appTrace  Trace object for error logging
     * @param param  User factory parameters
     *
     * @return a credentials object as described by the parameter object given,
     *    or null if parameters were missing or invalid somehow.
     */
    DeviceCredentials extractCredentials(Trace appTrace,
                                         JsonObject param)
    {
        try {
            String uuid = param.getString("uuid");
            if (uuid == null) {
                appTrace.errorm("bad parameter: missing uuid");
                return null;
            }
            String name = param.getString("name");
            if (name == null) {
                name = param.getString("nickname");
            }
            return new DeviceCredentials(uuid, name);
        } catch (JSONDecodingException e) {
            appTrace.errorm("bad parameter: " + e);
        }
        return null;
    }

    /**
     * Struct object holding login info for a device user.
     */
    protected static class DeviceCredentials {
        /** The device ID */
        final String uuid;
        /** Name of the user */
        final String name;

        DeviceCredentials(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

}