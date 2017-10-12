package net.sf.chttpc;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import net.sf.chttpc.test.BaseTestSuite;
import net.sf.chttpc.test.EqualsIgnoreCase;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class HeadTests extends BaseTestSuite {
    private static Curl.CurlURLStreamHandler httpStreamHandler;

    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();

        httpStreamHandler = new Curl.ConnectionBuilder(InstrumentationRegistry.getTargetContext())
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

            assertThat(conn.getResponseCode()).isEqualTo(201);

            final RecordedRequest request = server.takeRequest();

            assertThat(request.getMethod()).isEqualTo("HEAD");
        }
    }

    @Test
    public void testHeadWithFactory() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            CurlConnection conn = httpStreamHandler.openConnection(new URL(server.url("/").toString()));

            conn.setRequestMethod("HEAD");

            assertThat(conn.getResponseCode()).isEqualTo(201);

            final RecordedRequest request = server.takeRequest();

            assertThat(request.getMethod()).isEqualTo("HEAD");
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

            assertThat(conn.getResponseCode()).isEqualTo(201);

            final RecordedRequest request = server.takeRequest();

            assertThat(request.getMethod()).isEqualTo("HEAD");
            assertThat(request.getHeader("X-Foobar")).isEqualTo("2");
        }
    }

    @Test
    public void testHeadAddEmptyClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.addRequestProperty("X-Foobar", "");

            assertThat(conn.getResponseCode()).isEqualTo(201);

            final RecordedRequest request = server.takeRequest();

            assertThat(request.getMethod()).isEqualTo("HEAD");
            assertThat(request.getHeader("X-Foobar")).isEqualTo("");
        }
    }

    @Test
    public void testHeadSetEmptyClientHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("X-Foobar", "");

            assertThat(conn.getResponseCode()).isEqualTo(201);

            final RecordedRequest request = server.takeRequest();

            assertThat(request.getMethod()).isEqualTo("HEAD");
            assertThat(request.getHeader("X-Foobar")).isEqualTo("");
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

    @Test
    public void testHeaderMap() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            String[] doodleducks = new String[] {
                    "Doodleduck-A", "1X" ,
                    "Doodleduck-B", "2X" ,
                    "Doodleduck-C", "3X" ,
                    "Doodleduck-D", "4X" ,
                    "Doodleduck-E", "5X" ,
                    "Doodleduck-F", "6X" ,
                    "Doodleduck-G", "7X" ,
                    "Doodleduck-H", "8X" ,
                    "Doodleduck-I", "9X" ,
                    "Doodleduck-J", "10X" ,
                    "Doodleduck-K", "11X" ,
                    "Doodleduck-L", "12X" ,
                    "Doodleduck-M", "13X" ,
            };

            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeaders(Headers.of(doodleducks)));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);
            assertEquals("HTTP/1.1 201 OK", conn.getHeaderField(0));
            assertNull(conn.getHeaderFieldKey(0));

            assertNull(conn.getHeaderField(doodleducks.length + 1));
            assertNull(conn.getHeaderField(Integer.MAX_VALUE));
            assertNull(conn.getHeaderField("oddduck"));

            final RecordedRequest request = server.takeRequest();

            assertEquals("HEAD", request.getMethod());

            Map<String, List<String>> map = conn.getHeaderFields();

            assertEquals(doodleducks.length / 2, map.size());

            for (int i = 2; i < doodleducks.length; i += 2) {
                List<String> list = map.get(doodleducks[i - 2]);

                assertEquals(1, list.size());
                assertEquals(doodleducks[i - 1], list.get(0));
            }
        }
    }

    @Test
    public void testResponseHeaderMultiMap() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            String[] doodleducks = new String[]{
                    "Doodleduck-A", "1X",
                    "Doodleduck-B", "2X",
                    "Doodleduck-C", "3X",
                    "Doodleduck-D", "4X",
                    "Doodleduck-E", "5X",
                    "Doodleduck-A", "2X",
                    "Doodleduck-F", "6X",
                    "Doodleduck-G", "7X",
                    "Doodleduck-H", "8X",
                    "Doodleduck-I", "9X",
                    "Doodleduck-J", "10X",
                    "Doodleduck-K", "11X",
                    "Doodleduck-L", "12X",
                    "Doodleduck-J", "32t24y4",
                    "Doodleduck-M", "",
                    "Doodleduck-J", "wlqr-q3ot4gwmnbwirnb",
                    "Doodleduck-J", "efqg",
                    "Doodleduck-J", "efqg",
                    "Doodleduck-B", "agehehr",
                    "Doodleduck-J", "d",
            };

            Headers hdrs = Headers.of(doodleducks);

            server.enqueue(new MockResponse()
                    .setHeaders(hdrs)
                    .setResponseCode(201));

            conn.setRequestMethod("HEAD");
            conn.setUrlString(server.url("/").toString());

            assertEquals(conn.getResponseCode(), 201);

            Map<String, List<String>> expectedMap = hdrs.toMultimap();
            Map<String, List<String>> actualMap = conn.getHeaderFields();

            assertThat(actualMap.entrySet())
                    .comparingElementsUsing(EqualsIgnoreCase.INSTANCE)
                    .containsExactlyElementsIn(expectedMap.entrySet());
        }
    }

    @Test
    public void testRequestHeader() throws Exception {
        CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

        String[] doodleducks = new String[]{
                "Doodleduck-A", "1X",
                "Doodleduck-B", "2X",
                "Doodleduck-C", "3X",
                "Doodleduck-D", "4X",
                "Doodleduck-E", "5X",
                "Doodleduck-A", "2X",
                "Doodleduck-F", "6X",
                "Doodleduck-G", "7X",
                "Doodleduck-H", "8X",
                "Doodleduck-I", "9X",
                "Doodleduck-J", "10X",
                "Doodleduck-K", "11X",
                "Doodleduck-L", "12X",
                "Doodleduck-J", "32t24y4",
                "Doodleduck-M", "",
                "Doodleduck-J", "wlqr-q3ot4gwmnbwirnb",
                "Doodleduck-J", "efqg",
                "Doodleduck-J", "efqg",
                "Doodleduck-B", "agehehr",
                "Doodleduck-J", "d",
        };

        Headers hdrs = Headers.of(doodleducks);

        for (int i = 0; i < doodleducks.length; i += 2) {
            String header = doodleducks[i];
            String value = doodleducks[i + 1];
            conn.addRequestProperty(header, value);
        }

        Map<String, List<String>> expectedMap = hdrs.toMultimap();

        for (String hdrName : hdrs.names()) {
            assertThat(conn.getRequestProperty(hdrName)).isEqualTo(expectedMap.get(hdrName).get(0));
        }
    }

    @Test
    public void testRequestHeaderMultiMap() throws Exception {
        CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

        String[] doodleducks = new String[]{
                "Doodleduck-A", "1X",
                "Doodleduck-B", "2X",
                "Doodleduck-C", "3X",
                "Doodleduck-D", "4X",
                "Doodleduck-E", "5X",
                "Doodleduck-A", "2X",
                "Doodleduck-F", "6X",
                "Doodleduck-G", "7X",
                "Doodleduck-H", "8X",
                "Doodleduck-I", "9X",
                "Doodleduck-J", "10X",
                "Doodleduck-K", "11X",
                "Doodleduck-L", "12X",
                "Doodleduck-J", "32t24y4",
                "Doodleduck-M", "",
                "Doodleduck-J", "wlqr-q3ot4gwmnbwirnb",
                "Doodleduck-J", "efqg",
                "Doodleduck-J", "efqg",
                "Doodleduck-B", "agehehr",
                "Doodleduck-J", "d",
        };

        Headers hdrs = Headers.of(doodleducks);

        for (int i = 0; i < doodleducks.length; i += 2) {
            String header = doodleducks[i];
            String value = doodleducks[i + 1];
            conn.addRequestProperty(header, value);
        }

        Map<String, List<String>> expectedMap = hdrs.toMultimap();
        Map<String, List<String>> actualMap = conn.getRequestProperties();

        assertThat(actualMap.entrySet())
                .comparingElementsUsing(EqualsIgnoreCase.INSTANCE)
                .containsExactlyElementsIn(expectedMap.entrySet());
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }
}
