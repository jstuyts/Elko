package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.PostInjectionInitializingObject
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import java.text.DateFormat
import java.text.ParseException
import java.time.Clock
import java.util.TimeZone

/**
 * Object representing the date and time at which a key or encumbrance will
 * cease to be valid.
 */
internal class ExpirationDate : Comparable<ExpirationDate>, Encodable, ClockUsingObject, PostInjectionInitializingObject {
    /** Millisecond clock time at which this expiration happens.  */
    private var myTime: Long = 0

    /** Singleton DateFormat object for parsing timestamp literals.  */
    private var theDateFmt: DateFormat? = null

    private lateinit var clock: Clock

    /**
     * Direct constructor.  The actual expiration time is represented by a
     * string that can take one of three forms:
     *   1) a decimal integer preceded by a "+" character, representing a
     *       millisecond Unix time value,
     *   2) the string "never", representing an "expiration" that never
     *       expires,
     *   3) a well-formed Java date literal that will be parsed by the Java
     *       DateFormat class.  Note that this latter form is provided as a
     *       convenience for testing, but probably shouldn't be used in
     *       production as the Java date parser is very finicky.
     *
     * @param dateString   String representation of the expiration date and
     *    time.
     */
    @Throws(ParseException::class)
    constructor(dateString: String, clock: Clock) {
        this.clock = clock
        theDateFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone(clock.zone)
            isLenient = true
        }
        myTime = when {
            dateString.startsWith("+") -> {
                try {
                    dateString.substring(1).toLong()
                } catch (e: NumberFormatException) {
                    throw ParseException("bad date format: $e", 0)
                }
            }
            dateString == "never" -> Long.MAX_VALUE
            else -> {
                val parsedDate = theDateFmt!!.parse(dateString)
                parsedDate.time
            }
        }
    }

    /**
     * JSON-driven constructor.
     *
     * @param time  Millisecond clock time when expiration happens
     */
    @JsonMethod("when")
    constructor(time: Long) {
        myTime = if (time == 0L) {
            Long.MAX_VALUE
        } else {
            time
        }
    }

    constructor(time: Long, clock: Clock) {
        setClock(clock)
        initialize()
        myTime = if (time == 0L) {
            Long.MAX_VALUE
        } else {
            time
        }
    }

    override fun setClock(clock: Clock) {
        this.clock = clock
    }

    override fun initialize() {
        theDateFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone(clock.zone)
            isLenient = true
        }
    }

    /**
     * Encode this expiration date for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this expiration date.
     */
    override fun encode(control: EncodeControl): JsonLiteral {
        val encTime = if (myTime == Long.MAX_VALUE) 0 else myTime
        return JsonLiteral(control).apply {
            addParameter("when", encTime)
            finish()
        }
    }

    /**
     * Compare this expiration date to another according to the dictates of the
     * standard Java Comparable interface.
     *
     * @param other  The other date to compare to.
     *
     * @return a value less than, equal to, or greater than zero according to
     * whether this expiration date is before, at, or after 'other'.
     */
    override fun compareTo(other: ExpirationDate): Int {
        val deltaTime = myTime - other.myTime
        return when {
            deltaTime < 0 -> -1
            deltaTime > 0 -> 1
            else -> 0
        }
    }

    /**
     * Test if this expiration date has already passed.
     *
     * @return true if this object represents an expired date, false if not.
     */
    val isExpired: Boolean
        get() = myTime < clock.millis()

    /**
     * Produce a legible representation of this expiration date.
     *
     * @return this expiration date as a string.
     */
    override fun toString() =
            if (myTime == Long.MAX_VALUE) "never" else "+$myTime"
}
