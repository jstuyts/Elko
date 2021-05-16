package org.elkoserver.feature.basicexamples.cartesian

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.model.BasicObject

/**
 * Create a 'move' message.
 *
 * @param target  Object the message is being sent to.
 * @param into  Container object is to be placed into (null if container is
 * not to be changed).
 * @param left  X coordinate of new position relative to container.
 * @param top  Y coordinate of new position relative to container.
 */
internal fun msgMove(target: Referenceable, into: BasicObject?, left: Int, top: Int) =
        JsonLiteralFactory.targetVerb(target, "move").apply {
            addParameterOpt("into", into as Referenceable?)
            addParameter("left", left)
            addParameter("top", top)
            finish()
        }
