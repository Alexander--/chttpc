package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FailureTests extends BaseTestSuite {
    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @Test(expected = MalformedURLException.class)
    public void testUrlBadProtocolError() throws Exception {
        CurlConnection conn = new CurlConnection(config);
        conn.setUrlString("brbr://tschchchchchchch");
        conn.connect();
    }

    @Test(expected = MalformedURLException.class)
    public void testMalformedUrl() throws Exception {
        CurlConnection conn = new CurlConnection(config);
        conn.setUrlString("");
        conn.connect();
    }

    @Test(expected = UnknownHostException.class)
    public void testDnsNoSuchHost() throws Exception {
        CurlConnection conn = new CurlConnection(config);
        conn.setUrlString("aaaaaaaaaaaaaaaaaaaaaapppsss");
        conn.getInputStream();
    }

    @Test(expected = SSLException.class)
    public void testSslHandshakeFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(factory, false);
            server.setProtocolNegotiationEnabled(false);

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = Exception.class)
    public void testSetMethodAfterConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.connect();

            conn.setRequestMethod("HEAD");
        }
    }

    @Test(expected = Exception.class)
    public void testInputWhenDoInputIsNotSet() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoInput(false);

            conn.getInputStream();
        }
    }

    @Test(expected = Exception.class)
    public void testOutputWhenDoOutputIsNotSet() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoOutput(false);

            conn.getOutputStream();
        }
    }

    @Test(expected = IOException.class)
    public void testGetInputStreamOnServerError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error!"));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.getInputStream();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderField("Content-Type");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect2() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderField(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingServerHeaderBeforeConnect3() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.getHeaderFieldKey(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGettingFullHeadersBeforeConnect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
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

            CurlConnection conn = new CurlConnection(config);
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

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = SocketTimeoutException.class, timeout = 2000)
    public void testConnTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

            conn.setConnectTimeout(200);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = SocketTimeoutException.class, timeout = 2000)
    public void terribleTimeoutShowcase() throws Exception {
        MockWebServer server = new MockWebServer();

        CurlConnection conn = new CurlConnection(config);

        server.enqueue(new MockResponse()
                .throttleBody(1, 10000, TimeUnit.DAYS)
                .setBody("xxxxxxxxxxxxxxxxxxxxxxxxxx"));

        conn.setUrlString(server.url("/").toString());
        conn.setReadTimeout(200);

        convertStreamToString(conn.getInputStream());
    }

    @Test(expected = IOException.class)
    public void testTcpConnectFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @Test(expected = IOException.class)
    public void testSuddenTcpFailureAfterRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());

            conn.getResponseCode();
        }
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }
}
