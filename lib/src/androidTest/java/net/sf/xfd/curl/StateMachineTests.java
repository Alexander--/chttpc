package net.sf.xfd.curl;

import android.support.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class StateMachineTests extends BaseTestSuite {
    @BeforeClass
    public static void setup() throws Exception {
        baseSetup();
    }

    @AfterClass
    public static void cleanup() {
        baseTeardown();
    }

    @Test
    public void errorStreamIsNullOn200() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(200));

            conn.getResponseCode();

            assertNull(conn.getErrorStream());
        }
    }

    @Test
    public void errorStreamIsNullOn300() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setHeader("Location", server.url("/").toString())
                    .setResponseCode(304));

            conn.setInstanceFollowRedirects(false);

            assertEquals(304, conn.getResponseCode());
            assertNull(conn.getErrorStream());
        }
    }

    @Test
    public void errorStreamIsNullBeforeConnect() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(501));

            assertNull(conn.getErrorStream());
        }
    }

    @Test
    public void errorStreamIsNotNullOn500() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(501));

            conn.getResponseCode();

            assertNotNull(conn.getErrorStream());
        }
    }

    @Test
    public void errorStreamIsNotNullOn400() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(400));

            conn.getResponseCode();

            assertNotNull(conn.getErrorStream());
        }
    }

    @Test
    public void testErrorInputWhenDoInputIsNotSet() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoInput(false);

            conn.connect();

            assertNull(conn.getErrorStream());
        }
    }

    @Test
    public void testErrorInputWhenDoInputIsNotSet2() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoInput(false);

            conn.connect();

            assertNotNull(conn.getErrorStream());
        }
    }
}
