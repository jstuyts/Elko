package org.elkoserver.feature.basicexamples.invisible

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.BasicObject
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher

/**
 * Marker mod to indicate that an item should be hidden from clients.  This mod
 * only makes sense when attached to items, not to contexts or users.
 *
 * If this mod is attached to an item, that item and its contents will be
 * omitted from the description of the containing context that is transmitted
 * to users who enter that context.
 */
class Invisible @JsonMethod internal constructor() : Mod(), ObjectCompletionWatcher, ItemMod {
    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral? =
            if (control.toRepository()) {
                JsonLiteralFactory.type("invisible", control).apply {
                    finish()
                }
            } else {
                null
            }

    /**
     * Mark the item as invisible, now that there's an item to mark.
     *
     * Application code should not call this method.
     */
    override fun objectIsComplete() {
        `object`().setVisibility(BasicObject.VIS_NONE)
    }
}
