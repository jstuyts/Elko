package org.elkoserver.foundation.json

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.elkoserver.json.JsonParsing
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Simple AES-based string encryptor/decryptor, for passing sealed bundles of
 * data through an untrusted party.
 *
 * @param keyStr Base64-encoded symmetric key.
 */
class Cryptor(keyStr: String, private val gorgel: Gorgel, private val jsonToObjectDeserializer: JsonToObjectDeserializer) {
    private val myCipher: Cipher
    private val myKey: SecretKey

    /**
     * Decode a key string.
     *
     * @param keyStr The base64-encoded bits of a symmetric key.
     * @return the given key, decoded, in the form of a SecretKey object.
     */
    private fun decodeKey(keyStr: String): SecretKey {
        val encodedKey = base64Decoder.decode(keyStr)
        return SecretKeySpec(encodedKey, KEY_ALGORITHM)
    }

    /**
     * Decrypt a (base-64 encoded) encrypted string
     *
     * @param str Encrypted string to be decrypted, as generated by the encrypt
     * method of this class.
     * @return the decrypted plaintext encoded in 'str'
     * @throws IOException if the input string is malformed.
     */
    fun decrypt(str: String): String {
        val ivStr = "${str.take(22)}=="
        val cypherText = str.substring(22)
        return try {
            val iv = base64Decoder.decode(ivStr)
            val ivSpec = IvParameterSpec(iv)
            myCipher.init(Cipher.DECRYPT_MODE, myKey, ivSpec)
            String(myCipher.doFinal(base64Decoder.decode(cypherText)),
                    StandardCharsets.UTF_8)
        } catch (e: InvalidAlgorithmParameterException) {
            gorgel.error("fatal Cryptor.decrypt failure: ", e)
            throw IllegalStateException(e)
        } catch (e: InvalidKeyException) {
            gorgel.error("fatal Cryptor.decrypt failure: ", e)
            throw IllegalStateException(e)
        } catch (e: BadPaddingException) {
            throw IOException("bad padding in cryptoblob $e")
        } catch (e: IllegalBlockSizeException) {
            throw IOException("bad block size in cryptoblob $e")
        }
    }

    /**
     * Decrypt a (base-64 encoded) encrypted JSON object literal.
     *
     * @param str Encrypted string to be decrypted.
     * @return The decrypted and parsed JSON object encoded in 'str'
     * @throws IOException         if the input string is malformed
     * @throws JsonParserException if the decrypted JSON literal is invalid
     */
    fun decryptJsonObject(str: String): JsonObject = JsonParsing.jsonObjectFromString(decrypt(str)) ?: throw IllegalStateException()

    /**
     * Decrypt and decode a (base-64 encoded) encrypted object serialized as a
     * JSON object literal.
     *
     * @param baseType The desired class of the resulting Java object.  The
     * result will not necessarily be of this class, but will be assignable
     * to a variable of this class.
     * @param str      Encrypted string to be decrypted.
     * @return a new Java object assignable to the class in 'baseType' as
     * described by the JSON literal obtained by decrypting 'str', or null
     * if the JSON literal was syntactically malformed or if the object
     * could not be decoded for some reason.
     * @throws IOException if the input string is malformed
     */
    fun decryptObject(baseType: Class<*>, str: String): Any? = jsonToObjectDeserializer.decode(baseType, decrypt(str))

    /**
     * Produce a (base-64 encoded) encrypted version of a string.
     *
     * The first 22 characters of the result string are the base64-encoded IV
     * (minus a terminal "==").  The remainder of the string is the
     * base64-encoded ciphertext.
     *
     * @param str String to be encrypted
     * @return an encoded, encrypted version of the given string
     */
    fun encrypt(str: String): String {
        val iv = ByteArray(16)
        theRandom.nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        val failure = try {
            myCipher.init(Cipher.ENCRYPT_MODE, myKey, ivSpec)
            return base64Encoder.encodeToString(iv).substring(0, 22) +
                    base64Encoder.encodeToString(myCipher.doFinal(str.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: InvalidAlgorithmParameterException) {
            e
        } catch (e: IllegalBlockSizeException) {
            e
        } catch (e: BadPaddingException) {
            e
        } catch (e: InvalidKeyException) {
            e
        }
        /* None of these should ever actually happen.  Die if they do. */
        gorgel.error("Cryptor.encrypt failure: ", failure)
        throw IllegalStateException(failure)
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()
        private val base64Decoder = Base64.getDecoder()

        @Deprecated("Obsolete code. Global variable")
        private val theRandom = SecureRandom()
        private const val KEY_ALGORITHM = "AES"
        private const val CRYPTO_ALGORITHM = "AES/CBC/PKCS5Padding"

        /**
         * Generate a new, random key.
         *
         * @return the generated key, as a string containing a base64-encoding of
         * the key bits.
         */
        fun generateKey(gorgel: Gorgel): String {
            return try {
                val key = KeyGenerator.getInstance(KEY_ALGORITHM).generateKey()
                base64Encoder.encodeToString(key.encoded)
            } catch (e: NoSuchAlgorithmException) {
                /* This should never actually happen. */
                gorgel.error("Cryptor.generateKey failure: unknown algorithm", e)
                throw IllegalStateException(e)
            }
        }
    }

    init {
        myKey = decodeKey(keyStr)
        myCipher = try {
            Cipher.getInstance(CRYPTO_ALGORITHM)

            /* These catches are required to satisfy the compiler.  In practice,
           neither of these exceptions can actually happen unless the runtime
           is radically misconfigured and critical crypto provider classes are
           missing, in which case the whole system is already hosed, so if
           either of these happens we're dead. */
        } catch (e: NoSuchAlgorithmException) {
            gorgel.error("Cryptor init failure: doesn't like algorithm 'AES'", e)
            throw IllegalStateException(e)
        } catch (e: NoSuchPaddingException) {
            gorgel.error("Cryptor init failure: doesn't like padding mode 'PKCS5Padding'", e)
            throw IllegalStateException(e)
        }
    }
}
