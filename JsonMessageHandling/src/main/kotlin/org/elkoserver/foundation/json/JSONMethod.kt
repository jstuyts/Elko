package org.elkoserver.foundation.json

/**
 * Annotation to mark methods as targets for JSON method dispatch and
 * constructors as decoders for JSON-driven object creation.
 *
 * The annotation value carries the JSON property names corresponding to
 * the parameters in the method or constructor parameter list.
 *
 * @param value Array of parameter names matching JSON parameter names to corresponding
 * parameter positions in the declared argument list.
 */
@MustBeDocumented
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CONSTRUCTOR)
annotation class JSONMethod(vararg val value: String = [])