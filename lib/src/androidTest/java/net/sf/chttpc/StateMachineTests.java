package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import com.google.common.truth.Truth;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Okio;

import static com.google.common.truth.Truth.assertThat;
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
    public static void cleanup() throws IOException {
        baseTeardown();
    }

    @Test
    public void errorStreamIsNullOn200() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

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
            CurlConnection conn = new CurlConnection(config);

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
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(501));

            assertNull(conn.getErrorStream());
        }
    }

    @Test
    public void errorStreamIsNotNullOn500() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

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
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setResponseCode(400));

            conn.getResponseCode();

            assertNotNull(conn.getErrorStream());
        }
    }

    @Test(expected = IOException.class)
    public void errorWhenReadingFromClosedInput() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setBody("Hi!")
                    .setResponseCode(200));

            InputStream is = conn.getInputStream();

            is.close();

            is.read();
        }
    }

    @Test(expected = IOException.class)
    public void errorWhenWritingToClosedOutput() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setBody("Hi!")
                    .setResponseCode(200));

            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(1);

            OutputStream os = conn.getOutputStream();

            os.close();

            os.write(1);
        }
    }

    @Test
    public void errorInputCloseIdempotent() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setBody("Hi!")
                    .setResponseCode(200));

            InputStream is = conn.getInputStream();

            is.close();
            is.close();
            is.close();
        }
    }

    @Test
    public void errorOutputCloseIdempotent() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            CurlConnection conn = new CurlConnection(config);

            conn.setUrlString(server.url("/").toString());

            server.enqueue(new MockResponse()
                    .setBody("Hi!")
                    .setResponseCode(200));

            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(1);

            OutputStream os = conn.getOutputStream();

            os.close();
            os.close();
            os.close();
        }
    }

    @Test
    public void testErrorInputWhenDoInputIsNotSet() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));

            CurlConnection conn = new CurlConnection(config);
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

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoInput(false);

            conn.connect();

            assertNotNull(conn.getErrorStream());
        }
    }

    @Test
    public void noHeaderAccumulationAcrossRedirects() throws Exception {
        try (MockWebServer server1 = new MockWebServer();
             MockWebServer server2 = new MockWebServer()) {
            server1.enqueue(new MockResponse()
                    .setResponseCode(304)
                    .addHeader("Location", server2.url("/"))
                    .addHeader("foobar1", "Hi"));

            server2.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("foobar2", "there!"));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server1.url("/").toString());
            conn.setDoInput(false);
            conn.setInstanceFollowRedirects(true);

            conn.connect();

            assertNull(conn.getHeaderField("foobar1"));
            assertEquals("there!", conn.getHeaderField("foobar2"));
        }
    }

    @Test
    public void outHeadersPreservation() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(297)
                    .setBody("1"));

            server.enqueue(new MockResponse()
                    .setResponseCode(298)
                    .setBody("2"));

            server.enqueue(new MockResponse()
                    .setResponseCode(299)
                    .setBody("3"));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.setRequestMethod("GET");
            conn.setDoInput(false);
            conn.addRequestProperty("testtest", "123");
            conn.setInstanceFollowRedirects(true);

            conn.connect();
            conn.reset();

            conn.connect();
            conn.reset();

            assertEquals("123", server.takeRequest().getHeader("testtest"));
            assertEquals("123", server.takeRequest().getHeader("testtest"));

            conn.getCurl().clearHeaders();
            conn.connect();

            assertNull(server.takeRequest().getHeader("testtest"));
        }
    }

    @Test
    public void streamsReusableAfterReset() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(297)
                    .setBody("1"));

            server.enqueue(new MockResponse()
                    .setResponseCode(298)
                    .setBody("2"));

            CurlConnection conn = new CurlConnection(config);
            conn.setUrlString(server.url("/").toString());
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);

            InputStream is1;
            OutputStream os1;

            try (Closeable c2 = os1 = conn.getOutputStream()) {
                os1.write(3);
            }

            try (Closeable c1 = is1 = conn.getInputStream()) {
                assertEquals("1", Okio.buffer(Okio.source(is1)).readUtf8());
            }

            conn.reset();

            InputStream is2;
            OutputStream os2;

            try (Closeable c2 = os2 = conn.getOutputStream()) {
                os2.write(4);
            }

            try (Closeable c1 = is2 = conn.getInputStream()) {
                assertEquals("2", Okio.buffer(Okio.source(is2)).readUtf8());
            }

            conn.reset();

            assertThat(is1).isNotSameAs(is2);
            assertThat(os1).isNotSameAs(os2);
        }
    }

    private static final int ough =                  0b1010011111;

    private static final int STATE_ATTACHED =        0b0000000001;
    private static final int STATE_HANDLE_REDIRECT = 0b0000000010;
    private static final int STATE_DO_INPUT =        0b0000000100;
    private static final int STATE_RECV_PAUSED =     0b0000001000;
    private static final int STATE_DO_OUTPUT =       0b0000010000;
    private static final int STATE_SEND_PAUSED =     0b0000100000;
    private static final int STATE_NEED_INPUT =      0b0001000000;
    private static final int STATE_NEED_OUTPUT =     0b0010000000;
    private static final int STATE_DONE_SENDING =    0b0100000000;
    private static final int STATE_SEEN_HEADER_END = 0b1000000000;
}
