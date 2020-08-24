package org.elkoserver.objdb

import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.json.JsonArray
import org.elkoserver.json.JsonObject
import org.elkoserver.json.JsonParsing.jsonObjectFromString
import org.elkoserver.objdb.store.ObjectDesc
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.HashMap
import java.util.LinkedList
import java.util.StringTokenizer
import java.util.function.Consumer

/**
 * Base class for both local and remote concrete implementations of the ObjDb
 * interface.
 */
abstract class ObjDbBase(
        protected val gorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer) : ObjDb {
    /** Table mapping JSON object type tags to Java classes.  */
    private val myClasses: MutableMap<String, Class<*>> = HashMap()

    /**
     * Inform the object database about a mapping from a JSON object type tag
     * string to a Java class.
     *
     * @param tag  The JSON object type tag string.
     * @param type  The class that 'tag' labels.
     */
    override fun addClass(tag: String, type: Class<*>) {
        myClasses[tag] = type
    }

    /**
     * Convert a parsed JSON object description into the object it describes.
     *
     * @param jsonObj  The object being decoded.
     *
     * @return the object described by 'jsonObj'.
     */
    fun decodeJSONObject(jsonObj: JsonObject): Any? {
        var result: Any? = null
        val typeTag = jsonObj.getString<String?>("type", null)
        if (typeTag != null) {
            val type = myClasses[typeTag]
            if (type != null) {
                result = jsonToObjectDeserializer.decode(type, jsonObj, this)
            } else {
                gorgel.error("no class for type tag '$typeTag'")
            }
        }
        return result
    }

    /**
     * Decode a collection of JSON strings into an object.
     *
     * @param ref  Reference string for the object to be decoded.
     * @param results  A collection of object descriptors; one of these will be
     * the object named by 'ref', others will be that object's contents.
     *
     * @return the decoded object, or null if it could not be decoded according
     * to the specified parameters.
     */
    fun decodeObject(ref: String, results: Array<ObjectDesc>): Any? {
        val objStr: String? = results
                .firstOrNull { ref == it.ref }
                ?.obj
        return if (objStr == null) {
            gorgel.error("no object retrieved from ObjDb for ref $ref")
            null
        } else {
            try {
                val jsonObj = jsonObjectFromString(objStr)!!
                insertContents(jsonObj, results)
                decodeJSONObject(jsonObj)
            } catch (e: JsonParserException) {
                gorgel.error("object store syntax error getting $ref: ${e.message}")
                null
            }
        }
    }

    /**
     * Given an object reference, obtain the object(s) it refers to, from among
     * the collection of objects retrieved by the store.  If the reference
     * value is a string, the output is the object referenced by that string.
     * If the reference value is a JSON array of strings, the output value is
     * an array of the objects referenced by those strings.  Otherwise, the
     * result is null.
     *
     * In the case of an array result, if all of the elments are of a common
     * type, then the result will be an array of that type.  Otherwise, the
     * result will be an array of Object.
     *
     * @param refValue  The value(s) of the property before dereferencing.
     * @param objs  The objects returned by the store.
     *
     * @return  The value(s) of the property after dereferencing.
     */
    private fun dereferenceValue(refValue: Any, objs: Array<ObjectDesc>): Any? {
        var result: Any? = null
        if (refValue is JsonArray) {
            val refs = refValue.iterator()
            val contents = arrayOfNulls<Any>(refValue.size())
            var resultClass: Class<*>? = null
            for (i in contents.indices) {
                val ref = refs.next()
                if (ref is String) {
                    val decodedObject = decodeObject(ref, objs)
                    contents[i] = decodedObject
                    if (decodedObject != null) {
                        val elemClass: Class<*> = decodedObject.javaClass
                        if (resultClass == null) {
                            resultClass = elemClass
                        } else if (elemClass.isAssignableFrom(resultClass)) {
                            resultClass = elemClass
                        } else if (!resultClass.isAssignableFrom(elemClass)) {
                            resultClass = Any::class.java
                        }
                    }
                } else {
                    contents[i] = null
                }
            }
            result = contents
            if (resultClass != Any::class.java) {
                result = java.lang.reflect.Array.newInstance(resultClass, contents.size)
                System.arraycopy(contents, 0, result, 0, contents.size)
            }
        } else if (refValue is String) {
            result = decodeObject(refValue, objs)
        }
        return result
    }

    /**
     * Replace the properties of a JsonObject that describe the object's
     * contents with the contents objects themselves, as retrieved by the
     * store.
     *
     * Any property whose name begins with "ref$" is treated as an object
     * reference.  It is removed from the object and replaced with a new
     * property whose name has the "ref$" prefix stripped off and whose value
     * is the object or objects referenced.
     *
     * @param obj  The JsonObject whose contents are to be inserted.
     * @param results  The results returned by the store.
     */
    private fun insertContents(obj: JsonObject, results: Array<ObjectDesc>) {
        var contentsProps: MutableList<Map.Entry<String, Any>>? = null
        val iter: MutableIterator<Map.Entry<String, Any>> = obj.entrySet().iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val propName = entry.key
            if (propName.startsWith("ref$")) {
                iter.remove()
                if (contentsProps == null) {
                    contentsProps = LinkedList()
                }
                contentsProps.add(entry)
            }
        }
        if (contentsProps != null) {
            for ((key, value) in contentsProps) {
                val prop = dereferenceValue(value, results)
                if (prop != null) {
                    obj.put(key.substring(4), prop)
                }
            }
        }
    }

    /**
     * Load one or more named class descriptor objects.
     *
     * @param classDescRefs  A comma separated list of class descriptor object
     * names.
     */
    fun loadClassDesc(classDescRefs: String?) {
        addClass("classes", ClassDesc::class.java)
        addClass("class", ClassTagDesc::class.java)
        getObject("classes", null, ClassDescReceiver("classes"))
        if (classDescRefs != null) {
            val tags = StringTokenizer(classDescRefs, " ,;:")
            while (tags.hasMoreTokens()) {
                val tag = tags.nextToken()
                getObject(tag, null, ClassDescReceiver(tag))
            }
        }
    }

    private inner class ClassDescReceiver(var myTag: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            val classes = obj as ClassDesc?
            if (classes != null) {
                gorgel.i?.run { info("loading classDesc '$myTag'") }
                classes.useInObjDb(this@ObjDbBase, gorgel)
            } else {
                gorgel.error("unable to load classDesc '$myTag'")
            }
        }

    }

    /**
     * Get the class associated with a given JSON type tag string.
     *
     * @param baseType  Base class from which result class must be derived.
     * @param typeName  JSON type tag identifying the desired class.
     *
     * @return a class named by 'typeName' suitable for assignment to a
     * method or constructor parameter of class 'baseType'.
     */
    override fun resolveType(baseType: Class<*>, typeName: String): Class<*>? {
        var result = myClasses[typeName]
        if (result != null && !baseType.isAssignableFrom(result)) {
            result = null
        }
        return result
    }

}