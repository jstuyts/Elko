package org.elkoserver.server.context.model

/**
 * Extract the base ID from an object reference that might refer to a
 * clone.
 *
 * @param ref  The reference to extract from.
 *
 * @return the base reference string embedded in 'ref', assuming it is a
 * clone reference (if it is not a clone reference, 'ref' itself will be
 * returned).
 */
internal fun extractBaseRef(ref: String): String {
    var dash = ref.indexOf('-')
    dash = ref.indexOf('-', dash + 1)
    return if (dash < 0) {
        ref
    } else {
        ref.take(dash)
    }
}
