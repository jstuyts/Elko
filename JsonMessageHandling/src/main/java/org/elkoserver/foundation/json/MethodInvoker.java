package org.elkoserver.foundation.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;

import org.elkoserver.json.JsonObject;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Invoker subclass for methods.  Uses Java reflection to invoke a JSON message
 * handler method.
 */
class MethodInvoker extends Invoker {
    /** The method to call. */
    private Method myMethod;

    /** The Java class that defined the method. */
    private Class<?> myMethodClass;

    /** Next method in a chain of methods for the same verb.  There will be one
        entry in this chain for each registered class that implements a handler
        for this verb. The operational assumption here is that verbs may be
        many, but the number of classes using any given verb will be small. */
    private MethodInvoker myNext;

    /**
     * Constructor.
     *
     * @param method  The message handler method itself.
     * @param paramTypes  The types of the various parameters (including the
     *    mandatory first Deliverer parameter).
     * @param paramNames  JSON names for the parameters.
     * @param next  Next JSON method in a growing chain.
     */
    MethodInvoker(Method method, Class<?>[] paramTypes, String[] paramNames,
                  MethodInvoker next, TraceFactory traceFactory, Clock clock)
    {
        super(method, paramTypes, paramNames, 1, traceFactory, clock);
        myMethod = method;
        myMethodClass = method.getDeclaringClass();
        myNext = next;
    }

    /**
     * Determine the object to which a message containing this method's verb
     * should really be delivered.
     *
     * @param target  The object to which the JSON message was addressed.
     *
     * @return the object to which the message should actually be delivered, or
     *    null if no appropriate object could be found.
     */
    DispatchTarget findActualTarget(DispatchTarget target) {
        if (target instanceof MessageRetargeter) {
            //noinspection unchecked
            return ((MessageRetargeter) target).findActualTarget((Class<? extends DispatchTarget>) myMethodClass);
        } else if (myMethodClass.isInstance(target)) {
            return target;
        } else {
            return null;
        }
    }

    /**
     * Invoke the method held by this invoker on a received JSON message.
     *
     * @param target  The object to which the message is targeted.  At this
     *    point it must already be determined that target is an object of the
     *    class for which 'myMethod' is a Method.
     * @param from  The entity from whom the message was received.
     * @param message  The message that was received.
     * @param resolver  Type resolver for parameters.
     */
    void handle(DispatchTarget target, Deliverer from, JsonObject message,
                TypeResolver resolver)
        throws MessageHandlerException
    {
        try {
            apply(target, from, message.entrySet(), resolver);
        } catch (JSONInvocationException e) {
            throw new MessageHandlerException("error calling JSON method", e);
        }
    }

    /**
     * Actually call the method.
     *
     * This method is called only from the superclass, Invoker.
     *
     * @param target  Invocation target.
     * @param params  Method parameters.
     *
     * @return null, since all JSON methods return void.
     */
    protected Object invokeMe(Object target, Object[] params)
        throws IllegalAccessException, InvocationTargetException,
            ParameterMismatchException
    {
        try {
            myMethod.invoke(target, params);
            return null;
        } catch (IllegalArgumentException e) {
            throw new ParameterMismatchException(params,
                                                 myMethod.getParameterTypes());
        }
    }

    /**
     * Follow the chain of linked methods of the same name.
     *
     * @return the next method in the chain of which this object is a part, or
     *    null if there are no more methods in the chain.
     */
    MethodInvoker next() {
        return myNext;
    }
}
