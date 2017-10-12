package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Protocol;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

import static com.google.common.truth.Truth.assertAbout;
import static net.sf.chttpc.test.Streams.inputStream;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class GetTests extends BaseTestSuite {
    private static final int HUGE_BUFFER = 10 * 1024 * 1024;

    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @Test
    public void testSuddenTcpFailureDuringSend() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY)
                    .setResponseCode(200));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
            conn.getContent();
        }
    }

    @Test
    public void testSslGetWithBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.SHUTDOWN_OUTPUT_AT_END)
                    .setResponseCode(200)
                    .setBody("No luck"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals("No luck", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);
        }
    }

    @Test
    public void testContentLength() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("No luck"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals("No luck", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());
            assertEquals("No luck".length(), conn.getHeaderFieldInt("content-length", -1));
        }
    }

    @Test
    public void testNonExistingDateHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("No luck"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals("No luck", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());
            assertEquals(-1, conn.getHeaderFieldDate("hubla-wubla", -1));
        }
    }

    @Test
    public void testDateHeader() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            // strip milliseconds
            long now = System.currentTimeMillis() / 1000;
            now *= 1000;

            final String formatted = dateFormat.format(now);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Date", formatted)
                    .setBody("No luck"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals("No luck", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());
            assertEquals(now, conn.getHeaderFieldDate("date", -1));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSslHttp2GetWithBody() throws Exception {
        // TODO figure out what's up with
        try (MockWebServer server = new MockWebServer()) {
            server.setProtocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(true);

            server.enqueue(new MockResponse()
                    .setHttp2ErrorCode(ErrorCode.NO_ERROR.httpCode)
                    .setBody("No luck"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals("No luck", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);
        }
    }

    @Test
    public void testSslGetNoBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse().setResponseCode(200));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            InputStream s = conn.getInputStream();

            assertEquals(-1, s.read());
            assertEquals(conn.getResponseCode(), 200);
        }
    }

    @Test
    public void testChunkedResponseBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            final String bigBody = "No luck!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.SHUTDOWN_OUTPUT_AT_END)
                    .setResponseCode(200)
                    .setChunkedBody(bigBody, 5));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            assertEquals(bigBody, convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);
        }
    }

    @Test(timeout = 6000)
    public void testSlowChunkedResponseBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            final String bigBody = "No luck!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.SHUTDOWN_OUTPUT_AT_END)
                    .setResponseCode(200)
                    .throttleBody(6, 200, TimeUnit.MILLISECONDS)
                    .setChunkedBody(bigBody, 8));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setUrlString(server.url("/").toString());

            assertEquals(bigBody, convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);
        }
    }

    @Test
    public void testRedirect() throws Exception {
        try (MockWebServer server1 = new MockWebServer();
             MockWebServer server2 = new MockWebServer()) {
            final String url1 = server1.url("/").toString();
            final String url2 = server2.url("/").toString();

            server1.enqueue(new MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", url2)
                    .setBody("You got redirected"));

            server2.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("Here we go"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(url1);
            conn.getResponseCode();

            assertEquals("Here we go", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());
            assertEquals(url2, conn.getURL().toString());
        }
    }

    @Test
    public void testRedirectWithoutRedirects() throws Exception {
        try (MockWebServer server1 = new MockWebServer();
             MockWebServer server2 = new MockWebServer()) {
            final String url1 = server1.url("/").toString();
            final String url2 = server2.url("/").toString();

            server1.enqueue(new MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", url2)
                    .setBody("You got redirected"));

            server2.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("Here we go"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(url1);
            conn.setInstanceFollowRedirects(false);
            conn.getResponseCode();

            assertEquals("You got redirected", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 302);
            assertEquals(url1, conn.getURL().toString());
        }
    }

    @Test
    public void testSslGet() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse()
                    .setStatus("HTTP/1.1 200 You are moron")
                    .addHeaderLenient("X-Hi", "              Welcome   ")
                    .setBody("Hi!"));

            conn.setUrlString(server.url("/").toString());
            conn.setRequestProperty("X-Foobar", "1");

            assertEquals(200, conn.getResponseCode());
            assertEquals("You are moron", conn.getResponseMessage());
            assertEquals("Welcome", conn.getHeaderField("X-Hi"));

            final RecordedRequest request = server.takeRequest();

            assertEquals("GET", request.getMethod());
            assertEquals("1", request.getHeader("X-Foobar"));
        }
    }

    @Test
    @SuppressWarnings("all")
    public void getDecentSizedFile() throws IOException {
        File f = File.createTempFile("egwt6u", "324trh");

        try (MockWebServer server = new MockWebServer()) {
            Buffer body = randomBody(HUGE_BUFFER);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(body.clone()));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            conn.setUrlString(server.url("/").toString());

            assertAbout(inputStream()).that(conn.getInputStream()).hasSameContentsAs(body.inputStream());
        } finally {
            f.delete();
        }
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }
}
