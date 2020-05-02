package org.elkoserver.foundation.json

import org.elkoserver.foundation.json.ParameterMismatchException
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.time.Clock

/**
 * Invoker subclass for constructors.  Uses Java reflection to invoke a
 * JSON-driven constructor that decodes a JSON object literal describing an
 * instance of a particular Java class.
 *
 * @param myConstructor  The JSON-driven constructor itself.
 * @param amIncludingRawObject  If true, pass the JSON object being decoded
 * as the first parameter to the constructor.
 * @param paramTypes  The types of the various parameters.
 * @param paramNames  JSON names for the parameters.
 */
internal class ConstructorInvoker(
        private val myConstructor: Constructor<*>,
        private val amIncludingRawObject: Boolean,
        paramTypes: Array<Class<*>>, paramNames: Array<out String>, traceFactory: TraceFactory, clock: Clock) : Invoker(myConstructor, paramTypes, paramNames, if (amIncludingRawObject) 1 else 0, traceFactory, clock) {

    /**
     * Invoke the constructor on a JSON object descriptor.
     *
     * @param obj  JSON object describing what is to be constructed.
     * @param resolver  Type resolver for parameters.
     *
     * @return the result of calling the constructor, or null if the
     * constructor failed.
     */
    fun construct(obj: JsonObject, resolver: TypeResolver): Any? {
        return try {
            tryToConstruct(obj, resolver)
        } catch (e: JSONInvocationException) {
            traceFactory.comm.errorm("error calling JSON constructor: " +
                    e.message)
            null
        } catch (e: MessageHandlerException) {
            var report = e.cause
            if (report == null) {
                report = e
            }
            traceFactory.comm.errorReportException(report,
                    "calling JSON constructor")
            null
        }
    }

    private fun tryToConstruct(obj: JsonObject, resolver: TypeResolver): Any? {
        val result = apply(null, if (amIncludingRawObject) obj else null, obj.entrySet(), resolver)

        // FIXME: Injectors must be injected, so they can be extended without having to touch this class
        if (result is ClockUsingObject) {
            result.setClock(clock)
        }
        if (result is TraceFactoryUsingObject) {
            result.setTraceFactory(traceFactory)
        }
        if (result is PostInjectionInitializingObject) {
            result.initialize()
        }
        return result
    }

    /**
     * Actually call the constructor.
     *
     * This method is called only from the superclass, Invoker.
     *
     * @param target  Invocation target (ignored in this case since there is
     * none for constructors).
     * @param params  Constructor parameters.
     *
     * @return the value returned by the constructor, or null if it failed.
     */
    @Throws(IllegalAccessException::class, InvocationTargetException::class, ParameterMismatchException::class)
    override fun invokeMe(target: Any?, params: Array<Any?>): Any? {
        return try {
            myConstructor.newInstance(*params)
        } catch (e: InstantiationException) {
            null
        } catch (e: IllegalArgumentException) {
            throw ParameterMismatchException(params,
                    myConstructor.parameterTypes)
        }
    }

    override fun toString(): String {
        return "Constructor($myConstructor)"
    }
}
