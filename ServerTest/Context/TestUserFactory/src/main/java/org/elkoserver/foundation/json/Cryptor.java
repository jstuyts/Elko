package org.elkoserver.foundation.json;

import org.apache.commons.codec.binary.Base64;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.util.trace.TraceFactory;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Simple AES-based string encryptor/decryptor, for passing sealed bundles of
 * data through an untrusted party.
 */
public class Cryptor {
    private Cipher myCipher;
    private SecretKey myKey;
    private TraceFactory traceFactory;
    private static Base64 theCodec = new Base64();
    private static SecureRandom theRandom = new SecureRandom();

    private static final String KEY_ALGORITHM = "AES";
    private static final String CRYPTO_ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * Constructor.
     *
     * @param keyStr  Base64-encoded symmetric key.
     *
     */
    public Cryptor(String keyStr, TraceFactory traceFactory) {
        myKey = decodeKey(keyStr);
        this.traceFactory = traceFactory;
        try {
            myCipher = Cipher.getInstance(CRYPTO_ALGORITHM);

        /* These catches are required to satisfy the compiler.  In practice,
           neither of these exceptions can actually happen unless the runtime
           is radically misconfigured and critical crypto provider classes are
           missing, in which case the whole system is already hosed, so if
           either of these happens we're dead. */
        } catch (NoSuchAlgorithmException e) {
            traceFactory.startup.fatalError("Cryptor init failure: doesn't like algorithm 'AES'", e);
            throw new IllegalStateException();
        } catch (NoSuchPaddingException e) {
            traceFactory.startup.fatalError("Cryptor init failure: doesn't like padding mode 'PKCS5Padding'", e);
            throw new IllegalStateException();
        }
    }

    /**
     * Decode a key string.
     *
     * @param keyStr  The base64-encoded bits of a symmetric key.
     *
     * @return the given key, decoded, in the form of a SecretKey object.
     *
     */
    private SecretKey decodeKey(String keyStr) {
        byte[] encodedKey = theCodec.decode(keyStr);
        return new SecretKeySpec(encodedKey, KEY_ALGORITHM);
    }

    /**
     * Decrypt a (base-64 encoded) encrypted string
     *
     * @param str Encrypted string to be decrypted, as generated by the encrypt
     *    method of this class.
     *
     * @return  the decrypted plaintext encoded in 'str'
     *
     * @throws IOException if the input string is malformed.
     */
    public String decrypt(String str) throws IOException {
        String ivStr = str.substring(0, 22) + "==";
        String cypherText = str.substring(22);

        try {
            byte[] iv = theCodec.decode(ivStr);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            myCipher.init(Cipher.DECRYPT_MODE, myKey, ivSpec);
            return new String(myCipher.doFinal(theCodec.decode(cypherText)),
                    StandardCharsets.UTF_8);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            traceFactory.startup.fatalError("fatal Cryptor.decrypt failure: ", e);
            throw new IllegalStateException();
        } catch (BadPaddingException e) {
            throw new IOException("bad padding in cryptoblob " + e);
        } catch (IllegalBlockSizeException e) {
            throw new IOException("bad block size in cryptoblob " + e);
        }
    }

    /**
     * Decrypt a (base-64 encoded) encrypted JSON object literal.
     *
     * @param str  Encrypted string to be decrypted.
     *
     * @return  The decrypted and parsed JSON object encoded in 'str'
     *
     * @throws IOException if the input string is malformed
     * @throws SyntaxError if the decrypted JSON literal is invalid
     */
    public JSONObject decryptJSONObject(String str)
        throws IOException, SyntaxError
    {
        return JSONObject.parse(decrypt(str));
    }

    /**
     * Decrypt and decode a (base-64 encoded) encrypted object serialized as a
     * JSON object literal.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     *    result will not necessarily be of this class, but will be assignable
     *    to a variable of this class.
     * @param str  Encrypted string to be decrypted.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     *    described by the JSON literal obtained by decrypting 'str', or null
     *    if the JSON literal was syntactically malformed or if the object
     *    could not be decoded for some reason.
     *
     * @throws IOException if the input string is malformed
     */
    public Object decryptObject(Class<?> baseType, String str)
        throws IOException
    {
        return ObjectDecoder.decode(baseType, decrypt(str), traceFactory);
    }

    /**
     * Produce a (base-64 encoded) encrypted version of a string.
     *
     * The first 22 characters of the result string are the base64-encoded IV
     * (minus a terminal "==").  The remainder of the string is the
     * base64-encoded ciphertext.
     *
     * @param str String to be encrypted
     *
     * @return an encoded, encrypted version of the given string
     */
    public String encrypt(String str) {
        byte[] iv = new byte[16];
        theRandom.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Exception failure;
        try {
            myCipher.init(Cipher.ENCRYPT_MODE, myKey, ivSpec);
            return theCodec.encodeToString(iv).substring(0, 22) +
                theCodec.encodeToString(myCipher.doFinal(str.getBytes(StandardCharsets.UTF_8)));
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException e) {
            failure = e;
        }
        /* None of these should ever actually happen.  Die if they do. */
        traceFactory.startup.fatalError("Cryptor.encrypt failure: ", failure);
        throw new IllegalStateException();
    }

    /**
     * Generate a new, random key.
     *
     * @return the generated key, as a string containing a base64-encoding of
     *    the key bits.
     */
    public static String generateKey(TraceFactory traceFactory) {
        try {
            SecretKey key =
                KeyGenerator.getInstance(KEY_ALGORITHM).generateKey();
            return theCodec.encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            /* This should never actually happen. */
            traceFactory.startup.fatalError("Cryptor.generateKey failure: unknown algorithm", e);
            throw new IllegalStateException();
        }
    }
}

