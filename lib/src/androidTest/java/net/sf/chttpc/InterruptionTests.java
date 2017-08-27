package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class InterruptionTests extends BaseTestSuite {
    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }

    @Test
    public void testReadInterruption() throws IOException, InterruptedException {
        try (final MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("Hi!")
                    .setBodyDelay(Long.MAX_VALUE, TimeUnit.MILLISECONDS));

            Thread t = new Thread("Interruption Test") {
                @Override
                public void run() {
                    CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
                    conn.setUrlString(server.url("/").toString());

                    try (InputStream stream = conn.getInputStream()) {
                        int r = stream.read();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            t.start();
            t.interrupt();
            t.join();
        }
    }

    @Test
    public void testReadInterruption2() throws IOException, InterruptedException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("Hi!")
                .setBodyDelay(Long.MAX_VALUE, TimeUnit.MILLISECONDS));

        Thread t = new Thread("Interruption Test") {
            @Override
            public void run() {
                CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
                conn.setUrlString(server.url("/").toString());

                try (InputStream stream = conn.getInputStream()) {
                    int r = stream.read();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        t.join(400);
        t.interrupt();
        t.join();
    }

    @Test
    public void testReadInterruptionProgress() throws IOException, InterruptedException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("abcxyz")
                .throttleBody(3, 1, TimeUnit.DAYS));

        final byte[] progress = new byte[6];

        FutureTask<?> t = new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
                conn.setUrlString(server.url("/").toString());

                try (InputStream stream = conn.getInputStream()) {
                    stream.read(progress, 0, 6);
                }

                return null;
            }
        });

        Thread q = new Thread(t, "Interruption Test");
        q.start();

        Thread.sleep(400);

        q.interrupt();

        assertEquals('a', progress[0]);
        assertEquals('b', progress[1]);
        assertEquals('c', progress[2]);

        assertEquals(0, progress[3]);
        assertEquals(0, progress[4]);
        assertEquals(0, progress[5]);

        try {
            t.get();
        } catch (ExecutionException e) {
            InterruptedIOException iie = (InterruptedIOException) e.getCause();

            assertEquals(3, iie.bytesTransferred);

            return;
        }

        fail();
    }
}
