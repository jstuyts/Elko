package org.elkoserver.feature.basicexamples.geo

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.ItemMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.UserMod

/**
 * Position class representing a latitude/longitude position on the surface of
 * the earth.
 *
 * @param lat  Latitude (decimal degrees)
 * @param lon  Longitude (decimal degrees)
 */
class GeoPosition @JsonMethod("lat", "lon") constructor(private val lat: Double, private val lon: Double) : Mod(), UserMod, ItemMod {

    /**
     * Encode this position for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this position.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("geopos", control).apply {
                addParameter("lat", lat)
                addParameter("lon", lon)
                finish()
            }

    override fun toString(): String = "(lat: $lat, lon: $lon)"

    companion object {
        /**
         * Generate a new geo-position and assign it to an object.
         *
         * @param obj  The object to be given the new position
         * @param lat  The new latitude
         * @param lon  The new longitude
         */
        fun setPosition(obj: BasicObject, lat: Double, lon: Double) {
            GeoPosition(lat, lon).attachTo(obj)
        }
    }
}
