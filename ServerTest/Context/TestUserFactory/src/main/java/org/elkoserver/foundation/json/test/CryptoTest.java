package org.elkoserver.foundation.json.test;

import org.elkoserver.foundation.json.Cryptor;
import org.elkoserver.foundation.properties.ElkoProperties;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.TraceController;
import org.elkoserver.util.trace.acceptor.file.TraceLog;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;

class CryptoTest {
    private static SecureRandom theRandom = new SecureRandom();

    private static void usage() {
        System.out.println("usage: java org.elkoserver.foundation.json.test.CryptoTest [key KEY] [plaintext PLAINTEXT] [cyphertext CYPHERTEXT] [nonce TIMEOUT USERNAME] [help]");
        System.exit(0);
    }

    private static final int CHAR_BASE = 0x20;
    private static final int CHAR_TOP  = 0x7e;
    private static final int CHAR_RANGE = CHAR_TOP - CHAR_BASE;
    private static final int NONCE_LENGTH = 20;

    private static String makeNonce(String timeout, String userName, Clock clock)
    {
        JSONLiteral nonce = new JSONLiteral();
        StringBuilder idstr = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; ++i) {
            idstr.append((char) (CHAR_BASE + theRandom.nextInt(CHAR_RANGE)));
        }
        nonce.addParameter("nonce", idstr.toString());
        nonce.addParameter("expire", clock.millis() / 1000 + Integer.parseInt(timeout));
        JSONLiteral user = new JSONLiteral("user", EncodeControl.forClient);
        user.addParameter("name", userName);
        user.finish();
        nonce.addParameter("user", user);
        nonce.finish();
        return nonce.sendableString();
    }

    public static void main(String[] args) {
        Clock clock = Clock.systemDefaultZone();
        ElkoProperties bootProperties = new ElkoProperties();
        TraceController traceController = new TraceController(new TraceLog(clock), clock);
        traceController.start(bootProperties);

        String keyStr = null;
        String plainText = "The crow flies at midnight";
        String cypherText = null;

        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "key":
                    keyStr = args[++i];
                    break;
                case "plaintext":
                    plainText = args[++i];
                    break;
                case "cyphertext":
                    cypherText = args[++i];
                    break;
                case "nonce":
                    String timeout = args[++i];
                    String user = args[++i];
                    plainText = makeNonce(timeout, user, clock);
                    break;
                case "help":
                    usage();
                    break;
                default:
                    System.out.println("don't recognize arg #" + i + " '" +
                            args[i] + "'");
                    usage();
                    break;
            }
        }

        if (keyStr == null) {
            keyStr = Cryptor.generateKey(traceController.getFactory());
        } 

        Cryptor cryptor = new Cryptor(keyStr, traceController.getFactory());

        if (cypherText != null) {
            try {
                plainText = cryptor.decrypt(cypherText);
            } catch (IOException e) {
                System.out.println("problem decrypting cyphertext: " + e);
                System.exit(2);
            }
        } else {
            cypherText = cryptor.encrypt(plainText);
        }
        
        System.out.println("Cyphertext: " + cypherText);
        System.out.println("Plaintext: " + plainText);
        System.out.println("Key: " + keyStr);
        System.out.println();
    }
}

