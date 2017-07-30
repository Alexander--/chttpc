package net.sf.xfd.hothttp;

import android.support.test.InstrumentationRegistry;

import net.sf.xfd.curl.CurlConnection;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.net.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

public class BaseTestSuite {
    protected static ReferenceQueue<Object> queue;
    protected static AtomicBoolean done;
    protected static Thread cleaner;
    protected static CurlConnection.Config config;
    protected static SSLSocketFactory factory;

    protected static void baseSetup() throws Exception {
        queue = new ReferenceQueue<>();
        done  = new AtomicBoolean();
        cleaner = new Thread() {
            @Override
            public void run() {
                super.run();

                do {
                    try {
                        Reference<?> c = queue.remove();
                        c.clear();
                        ((Closeable) c).close();
                    } catch (Throwable ignored) {
                        // ok
                    }
                } while (!done.get());
            }
        };
        cleaner.start();
        config = new CurlConnection.Config() {
            @Override
            public String getDnsServers() {
                return null;
            }

            @Override
            public String getNetworkInterface() {
                return null;
            }

            @Override
            public Proxy getProxy(String url) {
                return null;
            }
        };
        try (InputStream keystore = InstrumentationRegistry.getTargetContext().getResources().openRawResource(R.raw.keystore)) {
            factory = TLSSocketFactory.loadMockCertContext(keystore);
        }
    }

    protected static void baseTeardown() {
        done.set(true);
        cleaner.interrupt();
    }
}
