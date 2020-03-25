package org.elkoserver.foundation.net;

import org.elkoserver.util.trace.Trace;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Properties;

public class SslSetup {
    public static SSLContext setupSsl(Properties properties, String propertyNamePrefix, Trace trace) {
        SSLContext result;

        try {
            result = tryToSetupSsl(properties, propertyNamePrefix);

        /* A wide variety of different kinds of problems can happen here, all
           of which entail some form of system misconfiguration and are
           unrecoverable.  The only useful thing to do is die with an
           informative message and let higher powers try again later after
           they've fixed it. */
        } catch (GeneralSecurityException e) {
            trace.fatalError("problem initializing SSL", e);
            throw new IllegalStateException();
        } catch (FileNotFoundException e) {
            trace.fatalError("SSL key file not found", e);
            throw new IllegalStateException();
        } catch (IOException e) {
            trace.fatalError("problem reading SSL key file", e);
            throw new IllegalStateException();
        }

        return result;
    }

    private static SSLContext tryToSetupSsl(Properties properties, String propertyNamePrefix) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        SSLContext result;

        String keyStoreType = properties.getProperty(propertyNamePrefix + "keystoretype", "JKS");
        KeyStore keys = KeyStore.getInstance(keyStoreType);

        String keyPassword = properties.getProperty(propertyNamePrefix + "keypassword");
        char[] passwordChars = keyPassword.toCharArray();

        String keyFile = properties.getProperty(propertyNamePrefix + "keyfile");
        keys.load(new FileInputStream(keyFile), passwordChars);

        String keyManagerAlgorithm =
            properties.getProperty(propertyNamePrefix + "keymanageralgorithm", "SunX509");

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
        keyManagerFactory.init(keys, passwordChars);
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        result = SSLContext.getInstance("TLS");
        result.init(keyManagers, null, null);

        return result;
    }
}
