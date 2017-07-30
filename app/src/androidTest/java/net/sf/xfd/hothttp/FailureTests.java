package net.sf.xfd.hothttp;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import net.sf.xfd.curl.CurlConnection;
import net.sf.xfd.curl.CurlHttp;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

@RunWith(AndroidJUnit4.class)
public class FailureTests extends BaseTestSuite {
    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @Test(expected = MalformedURLException.class)
    public void testUrlBadProtocolError() throws Exception {
        CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
        conn.setUrlString("brbr://tschchchchchchch");
        conn.connect();
    }

    @Test(expected = MalformedURLException.class)
    public void testMalformedUrl() throws Exception {
        CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
        conn.setUrlString("");
        conn.connect();
    }

    @Test(expected = UnknownHostException.class)
    public void testDnsNoSuchHost() throws Exception {
        CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
        conn.setUrlString("aaaaaaaaaaaaaaaaaaaaaapppsss");
        conn.getInputStream();
    }

    @Test(expected = SSLException.class)
    public void testSslHandshakeFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderField("Content-Type");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect2() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderField(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect3() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderFieldKey(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingFullHeadersBeforeConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderFields();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testSettingClientHeaderAfterConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());
            conn.connect();

            conn.addRequestProperty("hi", "there");
        }
    }

    @Test(expected = SSLException.class)
    public void testSslConnectFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = SocketTimeoutException.class)
    public void testConnTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

            conn.setConnectTimeout(3000);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = SocketException.class)
    public void testTcpConnectFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = IOException.class)
    public void testSuddenTcpFailureAfterRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @AfterClass
    public static void cleanup() {
        baseTeardown();
    }
}
