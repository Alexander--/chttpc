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

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GetTests extends BaseTestSuite {
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

    @Test
    public void testSlowChunkedResponseBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            final String bigBody = "No luck!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.SHUTDOWN_OUTPUT_AT_END)
                    .setResponseCode(200)
                    .throttleBody(6, 500, TimeUnit.MILLISECONDS)
                    .setChunkedBody(bigBody, 8));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
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
            assertEquals(conn.getResponseCode(), 200);
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

            boolean crap = conn.getDoInput();

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

    private static String convertStreamToString(java.io.InputStream is) {
        try (Scanner scanner = new Scanner(is, "UTF-8")) {
            scanner.useLocale(new Locale("ru", "RU"));

            return scanner.useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            e.printStackTrace();

            return "";
        }
    }

    @AfterClass
    public static void cleanup() {
        baseTeardown();
    }
}
