package org.elkoserver.util.trace.slf4j

import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.reflect.KClass

/**
 * Logger with (optional) static tags (implemented as a [Marker]), which will be passed to each log method.
 *
 * Only use `tags(...)` functions to create [Marker] instances for methods accepting a marker. This will ensure that any
 * static tags are included in the returned marker.
 */
abstract class Gorgel protected constructor(protected val logger: Logger, protected val staticTags: Marker?) {

    /**
     * This `Gorgel` if debug logging is enabled, otherwise `null`. Example use:
     *
     * ````
     * gorgel.d?.debug("Debug is enabled: {}", <expensive object>)
     * gorgel.d?.run { debug(tags("some-dynamic-tag"), "Debug is enabled: {}", <expensive object>) }
     * ````
     */
    val d: Gorgel?
        get() = if (logger.isDebugEnabled) this else null

    fun debug(message: String) {
        logger.debug(staticTags, message)
    }

    fun debug(message: String, throwable: Throwable) {
        logger.debug(staticTags, message, throwable)
    }

    fun debug(marker: Marker, message: String) {
        logger.debug(marker, message)
    }

    fun debug(marker: Marker, message: String, throwable: Throwable) {
        logger.debug(marker, message, throwable)
    }

    /**
     * This `Gorgel` if info logging is enabled, otherwise `null`. Example use:
     *
     * ````
     * gorgel.i?.info("Info is enabled: {}", <expensive object>)
     * gorgel.i?.run { info(tags("some-dynamic-tag"), "Info is enabled: {}", <expensive object>) }
     * ````
     */
    val i: Gorgel?
        get() = if (logger.isInfoEnabled) this else null

    fun info(message: String) {
        logger.info(staticTags, message)
    }

    fun info(message: String, throwable: Throwable) {
        logger.info(staticTags, message, throwable)
    }

    fun info(marker: Marker, message: String) {
        logger.info(marker, message)
    }

    fun info(marker: Marker, message: String, throwable: Throwable) {
        logger.info(marker, message, throwable)
    }

    fun warn(message: String) {
        logger.warn(staticTags, message)
    }

    fun warn(message: String, throwable: Throwable) {
        logger.warn(staticTags, message, throwable)
    }

    fun warn(tags: Marker, message: String) {
        logger.warn(tags, message)
    }

    fun warn(tags: Marker, message: String, throwable: Throwable) {
        logger.warn(tags, message, throwable)
    }

    fun error(message: String) {
        logger.error(staticTags, message)
    }

    fun error(message: String, throwable: Throwable) {
        logger.error(staticTags, message, throwable)
    }

    fun error(tags: Marker, message: String) {
        logger.error(tags, message)
    }

    fun error(tags: Marker, message: String, throwable: Throwable) {
        logger.error(tags, message, throwable)
    }

    /**
     * Create a dynamic tag. This tag **MAY NOT** be stored and reused.
     *
     * @param tag The tag name.
     * @return A marker for the given tag name.
     */
    abstract fun tags(tag: String): Marker

    /**
     * Create 2 dynamic tags. These tags **MAY NOT** be stored and reused.
     *
     * @param firstTag The first tag name.
     * @param secondTag The second tag name.
     * @return A marker for the given tag names.
     */
    abstract fun tags(firstTag: String, secondTag: String): Marker

    /**
     * Create multiple dynamic tags. These tags **MAY NOT** be stored and reused.
     *
     * @param tags The tag names.
     * @return A marker for the given tag names.
     */
    abstract fun tags(vararg tags: String): Marker

    abstract fun withAdditionalStaticTags(tag: String): Gorgel

    abstract fun withAdditionalStaticTags(firstTag: String, secondTag: String): Gorgel

    abstract fun withAdditionalStaticTags(vararg tags: String): Gorgel

    abstract fun getChild(childName: String): Gorgel

    abstract fun getChild(theClass: KClass<*>): Gorgel

    abstract fun getChild(childName: String, tag: String): Gorgel

    abstract fun getChild(theClass: KClass<*>, tag: String): Gorgel

    abstract fun getChild(childName: String, firstTag: String, secondTag: String): Gorgel

    abstract fun getChild(theClass: KClass<*>, firstTag: String, secondTag: String): Gorgel

    abstract fun getChild(childName: String, vararg tags: String): Gorgel

    abstract fun getChild(theClass: KClass<*>, vararg tags: String): Gorgel
}
