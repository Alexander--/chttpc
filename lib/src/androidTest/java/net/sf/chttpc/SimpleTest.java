package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class SimpleTest extends BaseTestSuite {
    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }

    @Test
    public void reuseTest() throws IOException, InterruptedException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            server.enqueue(new MockResponse()
                    .setBody("Hi!")
                    .setResponseCode(200));

            server.enqueue(new MockResponse()
                    .setBody("Wow")
                    .setResponseCode(200));

            server.enqueue(new MockResponse()
                    .setBody("NF")
                    .setResponseCode(404));

            conn.setRequestMethod("HEAD");
            conn.addRequestProperty("Test", "ddd");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request1 = server.takeRequest();
            assertEquals("HEAD", request1.getMethod());
            assertEquals("ddd", request1.getHeader("Test"));

            conn.disconnect();

            conn.setRequestMethod("GET");
            conn.setUrlString(server.url("/").toString());
            assertEquals("ddd", request1.getHeader("Test"));

            assertEquals(conn.getResponseCode(), 200);

            final RecordedRequest request2 = server.takeRequest();
            assertEquals("GET", request2.getMethod());

            conn.disconnect();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Test", null);
            conn.setUrlString(server.url("/").toString());

            assertEquals(200, conn.getResponseCode());

            final RecordedRequest request3 = server.takeRequest();
            assertEquals("POST", request3.getMethod());
            assertNull(request3.getHeader("Test"));

            conn.disconnect();

            conn.setUrlString(server.url("/").toString());

            assertEquals(404, conn.getResponseCode());
        }
    }
}
