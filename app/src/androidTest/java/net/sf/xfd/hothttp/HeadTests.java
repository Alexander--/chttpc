package net.sf.xfd.hothttp;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import net.sf.xfd.curl.Curl;
import net.sf.xfd.curl.CurlConnection;
import net.sf.xfd.curl.CurlHttp;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.List;

import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class HeadTests extends BaseTestSuite {
    private static Curl.CurlURLStreamHandler httpStreamHandler;

    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();

        httpStreamHandler = Curl.builder(InstrumentationRegistry.getTargetContext())
                .build()
                .createURLStreamHandler("http");
    }

    @Test
    public void testHead() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
        }
    }

    @Test
    public void testHeadWithFactory() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            CurlConnection conn = httpStreamHandler.openConnection(new URL(server.url("/").toString()));

            conn.setRequestMethod("HEAD");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
        }
    }

    @Test
    public void testHeadAddClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("2", request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadAddMultipleClientHeadersWithSameKey() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.addRequestProperty("X-Foobar", "14");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());

            final Headers headers = request.getHeaders();
            final List<String> interestingValues = headers.values("X-Foobar");
            assertEquals(2, interestingValues.size());
            assertEquals("2", interestingValues.get(0));
            assertEquals("14", interestingValues.get(1));
        }
    }

    @Test
    public void testHeadAddMultipleClientHeadersWithDistinctKeys() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.addRequestProperty("efqegwegegwe", "uuU");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("2", request.getHeader("X-Foobar"));
            assertEquals("uuU", request.getHeader("efqegwegegwe"));
        }
    }

    @Test
    public void testHeadSetClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("X-Foobar", "2");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("2", request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadSetSameClientHeaderTwice() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("X-Foobar", "2");
            conn.setRequestProperty("X-Foobar", "9999");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("9999", request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadRemoveClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("X-Foobar", "2");
            conn.setRequestProperty("X-Foobar", null);

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertNull(request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadRemoveBuiltinClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("Accept", null);

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertNull(request.getHeader("Accept"));
        }
    }

    @Test
    public void testHeadReaddBuiltinClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("Accept", null);
            conn.setRequestProperty("Accept", "text/plain");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("text/plain", request.getHeader("Accept"));
        }
    }

    @Test
    public void testHeadRemoveMultipleClientHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.addRequestProperty("www", "Doodleduck");
            conn.setRequestProperty("www", null);
            conn.setRequestProperty("X-Foobar", null);

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertNull(request.getHeader("X-Foobar"));
            assertNull(request.getHeader("www"));
        }
    }

    @Test
    public void testHeadRemoveDuplicateClientHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.addRequestProperty("X-Foobar", "1000");
            conn.setRequestProperty("X-Foobar", null);

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertNull(request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadRemoveAndAddDuplicateClientHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.addRequestProperty("X-Foobar", "1000");
            conn.setRequestProperty("X-Foobar", null);
            conn.addRequestProperty("X-Foobar", "16");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("16", request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadReaddClientHeader2() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "2");
            conn.setRequestProperty("X-Foobar", null);
            conn.addRequestProperty("X-Foobar", "14");

            assertEquals(conn.getResponseCode(), 201);

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
            assertEquals("14", request.getHeader("X-Foobar"));
        }
    }

    @Test
    public void testHeadServerHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Doodleduck", "XX"));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);
            assertEquals("XX", conn.getHeaderField("Doodleduck"));

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
        }
    }

    @Test
    public void testSeveralServerHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            String[][] doodleducks = new String[][]{
                    { "Doodleduck-A", "1X" },
                    { "Doodleduck-B", "2X" },
                    { "Doodleduck-C", "3X" },
                    { "Doodleduck-D", "4X" },
                    { "Doodleduck-E", "5X" },
                    { "Doodleduck-F", "6X" },
                    { "Doodleduck-G", "7X" },
                    { "Doodleduck-H", "8X" },
                    { "Doodleduck-I", "9X" },
                    { "Doodleduck-J", "10X" },
                    { "Doodleduck-K", "11X" },
                    { "Doodleduck-L", "12X" },
                    { "Doodleduck-M", "13X" },
            };

            MockResponse response = new MockResponse()
                    .setResponseCode(201);

            for (String[] doodleduck : doodleducks) {
                response.addHeader(doodleduck[0], doodleduck[1]);
            }

            server.enqueue(response);

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);

            for (int i = 2; i < doodleducks.length; ++i) {
                assertEquals(doodleducks[i - 2][1], conn.getHeaderField(doodleducks[i - 2][0]));
                assertEquals(doodleducks[i - 2][1], conn.getHeaderField(i));
                assertEquals(doodleducks[i - 2][0], conn.getHeaderFieldKey(i));
            }

            assertEquals("HTTP/1.1 201 OK", conn.getHeaderField(0));
            assertNull(conn.getHeaderFieldKey(0));

            assertNull(conn.getHeaderField(doodleducks.length + 2));
            assertNull(conn.getHeaderField(Integer.MAX_VALUE));
            assertNull(conn.getHeaderField("oddduck"));

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());
        }
    }

    @AfterClass
    public static void cleanup() {
        baseTeardown();
    }
}
