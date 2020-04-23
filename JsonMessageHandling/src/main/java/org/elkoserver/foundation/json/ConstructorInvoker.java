package org.elkoserver.foundation.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;

import org.elkoserver.json.JsonObject;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Invoker subclass for constructors.  Uses Java reflection to invoke a
 * JSON-driven constructor that decodes a JSON object literal describing an
 * instance of a particular Java class.
 */
class ConstructorInvoker extends Invoker {
    /** The constructor to call. */
    private final Constructor<?> myConstructor;

    /** Flag to include the raw JSON object being decoded as a constructor
        parameter. */
    private final boolean amIncludingRawObject;

    /**
     * Constructor.
     *  @param constructor  The JSON-driven constructor itself.
     * @param includeRawObject  If true, pass the JSON object being decoded
     *    as the first parameter to the constructor.
     * @param paramTypes  The types of the various parameters.
     * @param paramNames  JSON names for the parameters.
     */
    ConstructorInvoker(Constructor<?> constructor, boolean includeRawObject,
                       Class<?>[] paramTypes, String[] paramNames, TraceFactory traceFactory, Clock clock)
    {
        super(constructor, paramTypes, paramNames, includeRawObject ? 1 : 0, traceFactory, clock);
        myConstructor = constructor;
        amIncludingRawObject = includeRawObject;
    }

    /**
     * Invoke the constructor on a JSON object descriptor.
     *
     * @param obj  JSON object describing what is to be constructed.
     * @param resolver  Type resolver for parameters.
     *
     * @return the result of calling the constructor, or null if the
     *    constructor failed.
     */
    Object construct(JsonObject obj, TypeResolver resolver) {
        try {
            return tryToConstruct(obj, resolver);
        } catch (JSONInvocationException e) {
            traceFactory.comm.errorm("error calling JSON constructor: " +
                              e.getMessage());
            return null;
        } catch (MessageHandlerException e) {
            Throwable report = e.getCause();
            if (report == null) {
                report = e;
            }
            traceFactory.comm.errorReportException(report,
                                            "calling JSON constructor");
            return null;
        }
    }

    private Object tryToConstruct(JsonObject obj, TypeResolver resolver) throws MessageHandlerException, JSONInvocationException {
        Object result = apply(null, amIncludingRawObject ? obj : null, obj.entrySet(), resolver);

        // FIXME: Injectors must be injected, so they can be extended without having to touch this class
        if (result instanceof ClockUsingObject) {
            ((ClockUsingObject)result).setClock(clock);
        }
        if (result instanceof TraceFactoryUsingObject) {
            ((TraceFactoryUsingObject)result).setTraceFactory(traceFactory);
        }

        if (result instanceof PostInjectionInitializingObject) {
            ((PostInjectionInitializingObject)result).initialize();
        }

        return result;
    }

    /**
     * Actually call the constructor.
     *
     * This method is called only from the superclass, Invoker.
     *
     * @param target  Invocation target (ignored in this case since there is
     *    none for constructors).
     * @param params  Constructor parameters.
     *
     * @return the value returned by the constructor, or null if it failed.
     */
    protected Object invokeMe(Object target, Object[] params)
        throws IllegalAccessException, InvocationTargetException,
            ParameterMismatchException
    {
        try {
            return myConstructor.newInstance(params);
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalArgumentException e) {
            throw new ParameterMismatchException(params,
                myConstructor.getParameterTypes());
        }
    }
}
