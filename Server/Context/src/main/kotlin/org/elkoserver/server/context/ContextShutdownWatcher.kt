package org.elkoserver.server.context

/**
 * Interface implemented by objects that wish to be notified when their
 * containing context is about to be shutdown.
 *
 * When the last user leaves a context and there are no objects retaining
 * the context to keep it open, the server shuts the context down.  Any
 * modified state associated with the context or any of the objects contained
 * by it is written to persistent storage and the context and its attendent
 * objects become eligible for garbage collection.  Prior to doing this,
 * however, the server first calls the [.noteContextShutdown] method of
 * each object implementing this interface that has registered an interest by
 * called the context's [ registerContextShutdownWatcher()][Context.registerContextShutdownWatcher] method.
 *
 * Instances of subclasses of [Mod] that implement this interface are
 * automatically registered when they are attached to the context or to an
 * object contained by the context.
 */
interface ContextShutdownWatcher {
    /**
     * Do whatever you want when the context shuts down.
     *
     * Whenever a context is about to be shutdown, the server will call this
     * method on all objects that have registered an interest in that context
     * via the context's [ registerContextShutdownWatcher()][Context.registerContextShutdownWatcher] method.
     */
    fun noteContextShutdown()
}