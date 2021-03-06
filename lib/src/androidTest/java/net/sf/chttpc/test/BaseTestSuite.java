package net.sf.chttpc.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import net.sf.chttpc.*;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.net.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import okio.Buffer;

public class BaseTestSuite {
    // minimal heap size is 16MB, so...
    protected static final int HUGE_BUFFER = 10 * 1024 * 1024;

    static {
        System.setProperty(CurlHttp.DEBUG, "true");
    }

    protected static ReferenceQueue<Object> queue;
    protected static AtomicBoolean done;
    protected static Thread cleaner;
    protected static CurlConnection.Config config;
    protected static SSLSocketFactory factory;
    protected static FileInputStream random;

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
                    } catch (InterruptedException done) {
                        return;
                    } catch (Throwable t) {
                        throw new AssertionError(t);
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
        random = new FileInputStream("/dev/urandom");
    }

    protected static Buffer randomBody(long size) throws IOException {
        final Buffer bodyBuffer = new Buffer();

        bodyBuffer.readFrom(random, size);

        return bodyBuffer;
    }

    protected static void baseTeardown() throws IOException {
        done.set(true);
        cleaner.interrupt();
        random.close();
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

    protected void populateFromRandom(OutputStream outputStream) throws IOException {
        new Buffer().readFrom(random, HUGE_BUFFER).writeTo(outputStream);
    }
}
