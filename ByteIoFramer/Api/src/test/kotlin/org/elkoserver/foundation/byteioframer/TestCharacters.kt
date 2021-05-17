package org.elkoserver.foundation.byteioframer

const val LF = '\n'.code.toByte()
const val CR = '\r'.code.toByte()
const val A = 'a'.code.toByte()
const val SOME_ASCII_BYTE = A
const val NON_ASCII_BYTE: Byte = 0x80.toByte()
const val SOME_NON_UTF8_CONTINUATION_BYTE = A

const val _80 = 0x80.toByte()
const val _BF = 0xBF.toByte()
const val _C0 = 0xC0.toByte()
const val _E0 = 0xE0.toByte()
const val _F0 = 0xF0.toByte()
const val _F3 = 0xF3.toByte()
const val _FF = 0xFF.toByte()
