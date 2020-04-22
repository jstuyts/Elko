package org.elkoserver.json;

import java.util.ArrayList;

/**
 * A parsed JSON array.
 *
 * This class represents a JSON array that has been received or is being
 * constructed.
 */
public class JSONArray extends ArrayList<Object> {
    /**
     * Construct a new, empty array.
     */
    public JSONArray() {
        super();
    }

    /**
     * Obtain the JSON object value of an element.
     *
     * @param index  The index of the object value sought.
     *
     * @return  The JSON object value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a JSON object.
     */
    public JSONObject getObject(int index) throws JSONDecodingException {
        try {
            return (JSONObject) get(index);
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain a printable string representation of this JSON array.
     *
     * @return a printable representation of this array.
     */
    public String toString() {
        return JsonArraySerialization.literal(this, EncodeControl.forRepository).sendableString();
    }
}
