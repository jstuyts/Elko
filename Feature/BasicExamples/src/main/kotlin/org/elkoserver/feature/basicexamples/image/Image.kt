package org.elkoserver.feature.basicexamples.image

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.GeneralMod
import org.elkoserver.server.context.Mod

/**
 * Mod to associate an image with an object.  This mod may be attached to a
 * context or a user or an item.
 *
 * This mod has no behavioral repertoire of its own, but simply holds onto
 * descriptive information for the benefit of the client.
 *
 * @param myWidth  Horizontal extent of the image (optional).
 * @param myHeight  Vertical extent of the image (optional).
 * @param myImg  URL of the image itself.
 */
class Image @JsonMethod("width", "height", "img") constructor(private val myWidth: OptInteger, private val myHeight: OptInteger, private val myImg: String) : Mod(), GeneralMod {

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            JsonLiteralFactory.type("image", control).apply {
                if (myWidth.present) {
                    addParameter("width", myWidth.value())
                }
                if (myHeight.present) {
                    addParameter("height", myHeight.value())
                }
                addParameter("img", myImg)
                finish()
            }
}
