package org.elkoserver.foundation.servermanagement

import java.nio.charset.StandardCharsets

internal fun String.toUtf8() = this.toByteArray(StandardCharsets.UTF_8)
