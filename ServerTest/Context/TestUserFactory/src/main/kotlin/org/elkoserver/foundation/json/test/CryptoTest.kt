package org.elkoserver.foundation.json.test

import org.elkoserver.foundation.json.BaseCommGorgelInjector
import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.Cryptor
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.util.trace.slf4j.GorgelImpl
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.io.IOException
import java.security.SecureRandom
import java.time.Clock
import kotlin.system.exitProcess

internal object CryptoTest {
    private val theRandom = SecureRandom()
    private fun usage() {
        println("usage: java org.elkoserver.foundation.json.test.CryptoTest [key KEY] [plaintext PLAINTEXT] [cyphertext CYPHERTEXT] [nonce TIMEOUT USERNAME] [help]")
        exitProcess(0)
    }

    private const val CHAR_BASE = 0x20
    private const val CHAR_TOP = 0x7e
    private const val CHAR_RANGE = CHAR_TOP - CHAR_BASE
    private const val NONCE_LENGTH = 20
    private fun makeNonce(timeout: String, userName: String, clock: Clock): String {
        val nonce = JsonLiteral().apply {
            val idstr = StringBuilder(NONCE_LENGTH)
            for (i in 0 until NONCE_LENGTH) {
                idstr.append((CHAR_BASE + theRandom.nextInt(CHAR_RANGE)).toChar())
            }
            addParameter("nonce", idstr.toString())
            addParameter("expire", clock.millis() / 1000 + timeout.toInt())
            val user = JsonLiteralFactory.type("user", ForClientEncodeControl).apply {
                addParameter("name", userName)
                finish()
            }
            addParameter("user", user)
            finish()
        }
        return nonce.sendableString()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val clock = Clock.systemDefaultZone()
        var keyStr: String? = null
        var plainText = "The crow flies at midnight"
        var cypherText: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "key" -> keyStr = args[++i]
                "plaintext" -> plainText = args[++i]
                "cyphertext" -> cypherText = args[++i]
                "nonce" -> {
                    val timeout = args[++i]
                    val user = args[++i]
                    plainText = makeNonce(timeout, user, clock)
                }
                "help" -> usage()
                else -> {
                    println("don't recognize arg #$i '${args[i]}'")
                    usage()
                }
            }
            ++i
        }
        val cryptorGorgel = GorgelImpl(LoggerFactory.getLogger(Cryptor::class.java),
                LoggerFactory.getILoggerFactory(),
                MarkerFactory.getIMarkerFactory())
        if (keyStr == null) {
            keyStr = Cryptor.generateKey(cryptorGorgel)
        }
        val jsonToObjectDeserializer = JsonToObjectDeserializer(
                GorgelImpl(LoggerFactory.getLogger(JsonToObjectDeserializer::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory()),
                GorgelImpl(LoggerFactory.getLogger(ConstructorInvoker::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory(), COMMUNICATION_CATEGORY_TAG),
                listOf(
                        ClockInjector(clock),
                        BaseCommGorgelInjector(GorgelImpl(LoggerFactory.getLogger(ROOT_LOGGER_NAME), LoggerFactory.getILoggerFactory(), MarkerFactory.getIMarkerFactory(), COMMUNICATION_CATEGORY_TAG))))
        val cryptor = Cryptor(keyStr, cryptorGorgel, jsonToObjectDeserializer)
        if (cypherText != null) {
            try {
                plainText = cryptor.decrypt(cypherText)
            } catch (e: IOException) {
                println("problem decrypting cyphertext: $e")
                exitProcess(2)
            }
        } else {
            cypherText = cryptor.encrypt(plainText)
        }
        println("Cyphertext: $cypherText")
        println("Plaintext: $plainText")
        println("Key: $keyStr")
        println()
    }
}
