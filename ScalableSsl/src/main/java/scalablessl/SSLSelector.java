package scalablessl;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelector;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import scalablessl.spi.SSLSelectorProvider;

/**
 * @deprecated Switch to the SSL implementation in the JDK.
 */
@Deprecated
public abstract class SSLSelector extends AbstractSelector
{
    protected SSLSelector(SSLSelectorProvider provider) throws IOException {
        super(provider);
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static SSLSelector open(String protocol)
        throws SSLException,
               IOException,
               NoSuchAlgorithmException,
               KeyStoreException,
               KeyManagementException,
               UnrecoverableKeyException,
               CertificateException
    {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static SSLSelector open(SSLContext context) throws IOException {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static Selector open() throws IOException {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }
}
