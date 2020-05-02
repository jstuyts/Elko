package org.elkoserver.foundation.json

import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.TraceFactory
import java.lang.reflect.Modifier
import java.time.Clock
import java.util.HashMap
import java.util.HashSet

/**
 * A collection of precomputed Java reflection information that can dispatch
 * JSON messages to methods of the appropriate classes.
 *
 * @param resolver Type resolver for the type tags of JSON encoded message
 *    parameter objects.
 */
class MessageDispatcher(private val myResolver: TypeResolver?, private val traceFactory: TraceFactory, private val clock: Clock) {
    /** Mapping of message verbs to MethodInvoker objects.  Each entry is
     * actually the head of a linked list of MethodInvoker objects, each of
     * which handles the verb for a different class.  To dispatch an incoming
     * JSON message, the verb is used as a key to obtain the head of the
     * handler list for that verb, then this list is searched sequentially for
     * a handler that matches a class acceptable to the object to which the
     * message was actually addressed.  */
    private val myInvokers: MutableMap<String, MethodInvoker> = HashMap()

    /** Classes for which there is stored dispatch information, to avoid
     * repeating reflection operations.  */
    private val myClasses: MutableSet<Class<*>> = HashSet()

    /**
     * Perform the Java reflection operations needed to do JSON message
     * dispatch on a given Java class.
     *
     * The class must be a JSON message handler class.  Such classes have JSON
     * message handler methods marked with the [JSONMethod] annotation.
     * JSON message handler methods must have public scope, a return type of
     * void, and a least one parameter, the first of which must have a type
     * assignable to a variable of type [Deliverer].
     *
     * The name of the method is the name of the JSON message verb that the
     * method handles.  The value of the attached [JSONMethod] annotation
     * is an array of Strings, one for each method parameter except the initial
     * [Deliverer] parameter.  These strings will be the names of JSON
     * message parameters and will be mapped one-to-one to the corresponding
     * parameters of the method itself when it is invoked to handle a JSON
     * message.
     *
     * If a method is annotated [JSONMethod] but does not follow these
     * rules, no dispatch information will be recorded for that method and an
     * error message will be logged.
     *
     * @param targetClass  Class to compute method dispatch information for.
     *
     * @throws JSONSetupError if an annotated method breaks the rules for a
     * JSON method.
     */
    fun addClass(targetClass: Class<*>) {
        if (!myClasses.contains(targetClass)) {
            for (method in targetClass.methods) {
                val note = method.getAnnotation(JSONMethod::class.java) ?: continue
                if (!Modifier.isPublic(method.modifiers)) {
                    throw JSONSetupError("class " + targetClass.name +
                            " JSON message handler method " + method.name +
                            " is not public")
                }
                if (method.returnType != Void.TYPE) {
                    throw JSONSetupError("class " + targetClass.name +
                            " JSON message handler method " + method.name +
                            " does not have return type void")
                }
                val paramTypes = method.parameterTypes
                if (paramTypes.size == 0 ||
                        !Deliverer::class.java.isAssignableFrom(paramTypes[0])) {
                    throw JSONSetupError("class " + targetClass.name +
                            " JSON message handler method " + method.name +
                            " does not have a Deliverer first parameter")
                }
                val paramNames: Array<out String> = note.value
                if (paramNames.size + 1 != paramTypes.size) {
                    throw JSONSetupError("class " + targetClass.name +
                            " JSON message handler method " + method.name +
                            " has wrong number of parameters")
                }
                val name = method.name
                val prev = myInvokers[name]
                myInvokers[name] = MethodInvoker(method, paramTypes, paramNames, prev, traceFactory, clock)
            }
            myClasses.add(targetClass)
        }
    }

    /**
     * Dispatch a received JSON message by invoking the appropriate JSON method
     * on the appropriate object with the parameters from the message.
     * This proceeds as follows:
     *
     * First, if 'from' is an instance of [SourceRetargeter], then
     * 'from' is replaced with result of calling its [ ][SourceRetargeter.findEffectiveSource] method.
     *
     * Second, if 'target' is an instance of [MessageRetargeter], then
     * 'target' is replaced with the result of calling its [ ][MessageRetargeter.findActualTarget] method.  This
     * step is repeated as many times as necessary until 'target' is no longer
     * an instance of [MessageRetargeter].
     *
     * If 'target' has a method with the same name as the message verb in
     * 'message' and which matches the message handler signature pattern as
     * described in the description of the [addClass()][.addClass] method,
     * then this method is invoked to handle the message and the message
     * dispatch operation is complete.  Note: for this to work, the 'target's
     * class must have previously been inserted into this dispatcher using the
     * [addClass()][.addClass] method.
     *
     * If the previous step failed to located a message handler method, but
     * 'target' is an instance of [DefaultDispatchTarget], then its
     * [handleMessage()][DefaultDispatchTarget.handleMessage] method is
     * invoked to handle the message and the message dispatch operation is
     * complete.  Otherwise a [MessageHandlerException] is thrown.
     *
     * @param from  The source from whom the message was allegedly received.
     * @param target  The object to which the message is addressed.
     * @param message  The message itself.
     *
     * @throws MessageHandlerException if there was some kind of problem
     * handling the message.
     */
    @Throws(MessageHandlerException::class)
    fun dispatchMessage(from: Deliverer?, target: DispatchTarget?,
                        message: JsonObject) {
        var from = from
        val verb = message.getString("op", null)
        if (verb != null) {
            var invoker = myInvokers[verb]
            while (invoker != null) {
                val actualTarget = invoker.findActualTarget(target)
                if (actualTarget != null) {
                    if (from is SourceRetargeter) {
                        from = (from as SourceRetargeter).findEffectiveSource(
                                target!!)
                    }
                    if (from == null) {
                        throw MessageHandlerException(
                                "invalid message target")
                    }
                    invoker.handle(actualTarget, from, message, myResolver)
                    return
                } else {
                    invoker = invoker.next()
                }
            }
            if (target is DefaultDispatchTarget) {
                val defaultTarget = target as DefaultDispatchTarget
                if (from is SourceRetargeter) {
                    from = (from as SourceRetargeter).findEffectiveSource(target)
                }
                defaultTarget.handleMessage(from!!, message)
            } else {
                throw MessageHandlerException(
                        "no message handler method for verb '$verb'")
            }
        } else {
            throw MessageHandlerException("this message no verb")
        }
    }

}