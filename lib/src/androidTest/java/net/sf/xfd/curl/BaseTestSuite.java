package net.sf.xfd.curl;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.net.Proxy;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import net.sf.xfd.curl.test.R;

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
            public String getNetworkInterface(@NonNull MutableUrl url) {
                return null;
            }

            @Override
            public Proxy getProxy(@NonNull MutableUrl url) {
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

    protected static String convertStreamToString(InputStream is) throws IOException {
        try (Scanner scanner = new Scanner(is, "UTF-8")) {
            String result = null;

            try {
                result = scanner
                        .useLocale(new Locale("en", "US"))
                        .useDelimiter("\\A")
                        .next();
            } catch (java.util.NoSuchElementException e) {
                e.printStackTrace();
            }

            final IOException trueError = scanner.ioException();
            if (trueError != null) {
                throw trueError;
            }

            return result;
        }
    }
}
