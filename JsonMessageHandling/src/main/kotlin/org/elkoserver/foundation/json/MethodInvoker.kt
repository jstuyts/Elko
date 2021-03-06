package org.elkoserver.foundation.json

import com.grack.nanojson.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Invoker subclass for methods.  Uses Java reflection to invoke a JSON message
 * handler method.
 *
 * @param myMethod  The message handler method itself.
 * @param paramTypes  The types of the various parameters (including the
 *    mandatory first Deliverer parameter).
 * @param paramNames  JSON names for the parameters.
 * @param next  Next JSON method in a growing chain.
 */
class MethodInvoker(
        private val myMethod: Method,
        paramTypes: Array<Class<*>>,
        paramNames: Array<out String>,
        internal val next: MethodInvoker?,
        commGorgel: Gorgel,
        jsonToObjectDeserializer: JsonToObjectDeserializer) : Invoker<Any>(myMethod, paramTypes, paramNames, 1, commGorgel, jsonToObjectDeserializer) {

    /** The Java class that defined the method.  */
    @Suppress("UNCHECKED_CAST")
    private val myMethodClass: Class<out DispatchTarget> = myMethod.declaringClass.takeIf(DispatchTarget::class.java::isAssignableFrom)
            as? Class<out DispatchTarget>
            ?: throw IllegalStateException("Not a method of a dispatch target")

    /**
     * Determine the object to which a message containing this method's verb
     * should really be delivered.
     *
     * @param target  The object to which the JSON message was addressed.
     *
     * @return the object to which the message should actually be delivered, or
     * null if no appropriate object could be found.
     */
    fun findActualTarget(target: DispatchTarget): DispatchTarget? {
        return when {
            target is MessageRetargeter -> (target as MessageRetargeter).findActualTarget(myMethodClass)
            myMethodClass.isInstance(target) -> target
            else -> null
        }
    }

    /**
     * Invoke the method held by this invoker on a received JSON message.
     *
     * @param target  The object to which the message is targeted.  At this
     * point it must already be determined that target is an object of the
     * class for which 'myMethod' is a Method.
     * @param from  The entity from whom the message was received.
     * @param message  The message that was received.
     * @param resolver  Type resolver for parameters.
     */
    fun handle(target: DispatchTarget, from: Deliverer?, message: JsonObject, resolver: TypeResolver) {
        try {
            apply(target, from, message.entries, resolver)
        } catch (e: JsonInvocationException) {
            throw MessageHandlerException("error calling JSON method", e)
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
    @Throws(IllegalAccessException::class, InvocationTargetException::class, ParameterMismatchException::class)
    override fun invokeMe(target: Any, params: Array<Any?>): Any? {
        return try {
            myMethod.invoke(target, *params)
            null
        } catch (e: IllegalArgumentException) {
            throw ParameterMismatchException(params, myMethod.parameterTypes)
        }
    }

    override fun toString(): String = "Method($myMethod)"
}
