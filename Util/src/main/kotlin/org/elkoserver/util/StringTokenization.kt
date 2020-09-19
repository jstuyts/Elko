package org.elkoserver.util

fun String.tokenize(vararg delimiters: Char) = if (isEmpty()) emptyList() else split(*delimiters)
