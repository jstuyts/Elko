package org.elkoserver.util.trace.slf4j

import org.slf4j.Logger
import org.slf4j.Marker

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

    fun debug(messagePattern: String, argument: Any?) {
        logger.debug(staticTags, messagePattern, argument)
    }

    fun debug(messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.debug(staticTags, messagePattern, firstArgument, secondArgument)
    }

    fun debug(messagePattern: String, vararg arguments: Any?) {
        logger.debug(staticTags, messagePattern, *arguments)
    }

    fun debug(message: String, throwable: Throwable) {
        logger.debug(staticTags, message, throwable)
    }

    fun debug(marker: Marker, message: String) {
        logger.debug(marker, message)
    }

    fun debug(marker: Marker, messagePattern: String, argument: Any?) {
        logger.debug(marker, messagePattern, argument)
    }

    fun debug(marker: Marker, messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.debug(marker, messagePattern, firstArgument, secondArgument)
    }

    fun debug(marker: Marker, messagePattern: String, vararg arguments: Any?) {
        logger.debug(marker, messagePattern, *arguments)
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

    fun info(messagePattern: String, argument: Any?) {
        logger.info(staticTags, messagePattern, argument)
    }

    fun info(messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.info(staticTags, messagePattern, firstArgument, secondArgument)
    }

    fun info(messagePattern: String, vararg arguments: Any?) {
        logger.info(staticTags, messagePattern, *arguments)
    }

    fun info(message: String, throwable: Throwable) {
        logger.info(staticTags, message, throwable)
    }

    fun info(marker: Marker, message: String) {
        logger.info(marker, message)
    }

    fun info(marker: Marker, messagePattern: String, argument: Any?) {
        logger.info(marker, messagePattern, argument)
    }

    fun info(marker: Marker, messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.info(marker, messagePattern, firstArgument, secondArgument)
    }

    fun info(marker: Marker, messagePattern: String, vararg arguments: Any?) {
        logger.info(marker, messagePattern, *arguments)
    }

    fun info(marker: Marker, message: String, throwable: Throwable) {
        logger.info(marker, message, throwable)
    }

    fun warn(message: String) {
        logger.warn(staticTags, message)
    }

    fun warn(messagePattern: String, argument: Any?) {
        logger.warn(staticTags, messagePattern, argument)
    }

    fun warn(messagePattern: String, vararg arguments: Any?) {
        logger.warn(staticTags, messagePattern, *arguments)
    }

    fun warn(messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.warn(staticTags, messagePattern, firstArgument, secondArgument)
    }

    fun warn(message: String, throwable: Throwable) {
        logger.warn(staticTags, message, throwable)
    }

    fun warn(tags: Marker, message: String) {
        logger.warn(tags, message)
    }

    fun warn(tags: Marker, messagePattern: String, argument: Any?) {
        logger.warn(tags, messagePattern, argument)
    }

    fun warn(tags: Marker, messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.warn(tags, messagePattern, firstArgument, secondArgument)
    }

    fun warn(tags: Marker, messagePattern: String, vararg arguments: Any?) {
        logger.warn(tags, messagePattern, *arguments)
    }

    fun warn(tags: Marker, message: String, throwable: Throwable) {
        logger.warn(tags, message, throwable)
    }

    fun error(message: String) {
        logger.error(staticTags, message)
    }

    fun error(messagePattern: String, argument: Any?) {
        logger.error(staticTags, messagePattern, argument)
    }

    fun error(messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.error(staticTags, messagePattern, firstArgument, secondArgument)
    }

    fun error(messagePattern: String, vararg arguments: Any?) {
        logger.error(staticTags, messagePattern, *arguments)
    }

    fun error(message: String, throwable: Throwable) {
        logger.error(staticTags, message, throwable)
    }

    fun error(tags: Marker, message: String) {
        logger.error(tags, message)
    }

    fun error(tags: Marker, messagePattern: String, argument: Any?) {
        logger.error(tags, messagePattern, argument)
    }

    fun error(tags: Marker, messagePattern: String, firstArgument: Any?, secondArgument: Any?) {
        logger.error(tags, messagePattern, firstArgument, secondArgument)
    }

    fun error(tags: Marker, messagePattern: String, vararg arguments: Any?) {
        logger.error(tags, messagePattern, *arguments)
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

    abstract fun getChild(childName: String, tag: String): Gorgel

    abstract fun getChild(childName: String, firstTag: String, secondTag: String): Gorgel

    abstract fun getChild(childName: String, vararg tags: String): Gorgel
}
