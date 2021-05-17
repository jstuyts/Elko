package org.elkoserver.foundation.actor

/**
 * Extract the root from a reference string.  This is everything before the
 * second '-' character, or the whole string if there are fewer than two
 * '-' characters in it.  This is used in the addressing of clone objects.
 *
 * @param ref  The reference string whose root is sought.
 *
 * @return the root reference string extracted from 'ref'.
 */
internal fun rootRef(ref: String): String {
    var delim = 0
    var count = 0
    while (0 <= delim) {
        delim = ref.indexOf('-', delim)
        if (0 <= delim) {
            ++count
            if (count == 2) {
                return ref.take(delim)
            }
            ++delim
        }
    }
    return ref
}
