package org.elkoserver.server.context.model

/**
 * Interface implemented by mods that wish to be notified when an item is
 * added to or removed from the container they are attached to.
 *
 * To enable this notification, mods may implement this interface, though
 * only one mod per object may implement it.
 *
 * This interface is only useful when implemented by subclasses of [ ] that are attached to container objects of some kind.
 */
internal interface ContentsWatcher {
    /**
     * Do whatever you want when an item is added to the container.
     *
     * @param what  The item that was added.
     */
    fun noteContentsAddition(what: Item?)

    /**
     * Do whatever you want when an item is removed from the container.
     *
     * @param what  The item that was removed.
     */
    fun noteContentsRemoval(what: Item?)
}