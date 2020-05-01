package org.elkoserver.server.context

import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.Referenceable
import org.elkoserver.util.EmptyIterator
import java.util.LinkedList

/**
 * Holder for the contents of a basic object that is a container.
 *
 * An object only acquires one of these if it actually contains stuff.
 */
class Contents private constructor(private val myContents: MutableList<Item>? = LinkedList()) : Iterable<Item?> {

    /**
     * Add an item to these contents.
     *
     * @param item  The item to add.
     */
    private fun add(item: Item) {
        myContents!!.add(item)
    }

    /**
     * Encode these contents as a JSONLiteralArray object.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSONLiteralArray object representing these contents.
     */
    fun encode(control: EncodeControl) =
            JSONLiteralArray(control).apply {
                if (myContents != null) {
                    for (elem in myContents) {
                        if (control.toClient()) {
                            addElement(elem.ref())
                        } else if (!elem.isEphemeral) {
                            addElement(elem.baseRef())
                        }
                    }
                }
                finish()
            }

    /**
     * Get an iterator over these contents.
     *
     * @return an iterator over the items contained by this contents object.
     */
    override fun iterator() = myContents?.iterator() ?: EmptyIterator.emptyIterator()

    /**
     * Remove an item from these contents.
     *
     * @param item  The item to remove.
     *
     * @return true if the contents became empty as a result.
     */
    private fun remove(item: Item): Boolean {
        myContents!!.remove(item)
        return myContents.isEmpty()
    }

    /**
     * Transmit a description of these contents as a series of 'make' messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     */
    private fun sendContentsDescription(to: Deliverer, maker: Referenceable) {
        if (myContents != null) {
            for (elem in myContents) {
                elem.sendItemDescription(to, maker, false)
            }
        }
    }

    companion object {
        /** Marker object representing the "contents" of objects that are not
         * allowed to be containers.  */
        @JvmField
        val theVoidContents = Contents(null)

        /**
         * Transmit contents as a series of 'make' messages.
         *
         * @param to  Where to send the description.
         * @param maker  Maker object to address message to.
         * @param contents  The contents to transmit, if not null.
         */
        @JvmStatic
        fun sendContentsDescription(to: Deliverer, maker: Referenceable,
                                    contents: Contents?) {
            contents?.sendContentsDescription(to, maker)
        }

        /**
         * Add an item to a contents container, creating the container if
         * necessary to do so.
         *
         * @param contents  The contents to which the item is to be added, or null
         * if no contents yet exist.
         * @param item  The item to add.
         *
         * @return the contents (created if necessary), with 'item' in it.
         */
        @JvmStatic
        fun withContents(contents: Contents?, item: Item) =
                (contents ?: Contents()).apply {
                    add(item)
                }

        /**
         * Remove an item from a contents container.  It is not an error for the
         * item to not be in the container (or even for the container to not
         * exist).
         *
         * @param contents  The contents from which the item is to be removed.
         * @param item  The item to remove.
         *
         * @return the contents, without 'item' in it.
         */
        @JvmStatic
        fun withoutContents(contents: Contents?, item: Item) =
                if (contents == null) {
                    null
                } else {
                    if (contents.remove(item)) {
                        null
                    } else {
                        contents
                    }
                }
    }
}
