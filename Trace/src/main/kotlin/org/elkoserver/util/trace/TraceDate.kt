package org.elkoserver.util.trace

import java.util.*

/**
 * Return time in form YYYYMMDDHHMMSS.  This is a terse sortable time.
 * Fields are zero-padded as needed.
 */
fun terseCompleteDateString(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    var retval = cal[Calendar.YEAR].toString()
    retval += zeroFill(cal[Calendar.MONTH] + 1) +
            zeroFill(cal[Calendar.DAY_OF_MONTH]) +
            zeroFill(cal[Calendar.HOUR_OF_DAY]) +
            zeroFill(cal[Calendar.MINUTE]) +
            zeroFill(cal[Calendar.SECOND])
    return retval
}

/**
 * Convert an int to a string, filling on the left with zeros as needed to
 * ensure that it is at least two digits.
 */
private fun zeroFill(number: Int): String {
    return if (number <= 9) {
        "0$number"
    } else {
        number.toString()
    }
}
