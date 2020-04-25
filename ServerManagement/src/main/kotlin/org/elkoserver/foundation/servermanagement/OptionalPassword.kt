package org.elkoserver.foundation.servermanagement

private const val NO_PASSWORD_INDICATOR = "-"

internal fun isNoPasswordIndicator(password: String) = password == NO_PASSWORD_INDICATOR

internal fun toPasswordProperty(password: String) =
        if (isNoPasswordIndicator(password)) "" else """, "password":"$password""""
