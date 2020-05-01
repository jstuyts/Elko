package org.elkoserver.server.context

/**
 * Interface implemented by mods that wish to be notified when the item they
 * are attached to has its container changed.
 *
 *
 * To enable this notification, mods may implement this interface, though
 * only one mod per object may implement it.
 *
 *
 * This interface is only useful when implemented by item Mods.
 */
internal interface ContainerWatcher {
    /**
     * Do whatever you want when the item's container changes.
     *
     * @param oldContainer  The old container (which no longer contains the
     * item at the point this method is called)
     * @param newContainer  The new container (which now contains the item)
     */
    fun noteContainerChange(oldContainer: BasicObject?,
                            newContainer: BasicObject?)
}