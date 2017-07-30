package net.sf.xfd.hothttp;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

class TLSSocketFactory {
    static SSLSocketFactory loadMockCertContext(InputStream stream) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, IOException, CertificateException {
        char[] password = "111111".toCharArray();

        KeyStore serverKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        serverKeyStore.load(stream, password);

        String kmfAlgoritm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgoritm);
        kmf.init(serverKeyStore, password);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(kmfAlgoritm);
        trustManagerFactory.init(serverKeyStore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }
}