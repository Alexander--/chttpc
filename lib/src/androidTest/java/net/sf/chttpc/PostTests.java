package net.sf.chttpc;

import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class PostTests extends BaseTestSuite {
    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();

        config = new CurlConnection.Config() {
            @Override
            public String getDnsServers() {
                return null;
            }

            @Override
            public String getNetworkInterface(@NonNull MutableUrl url) {
                return null;
            }

            @Override
            public Proxy getProxy(@NonNull MutableUrl url) {
                return null;
            }
        };
    }

    @Test(timeout = 2000)
    public void testFixedLengthUploadContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("Nice")
                    .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE));

            final String bigBody = "Get this!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", null);
            conn.setFixedLengthStreamingMode(bigBody.length());
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            RecordedRequest request = server.takeRequest();

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 2000)
    public void testChunkedLengthUploadContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("Nice")
                    .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE));

            char[] chars = new char[128000];
            Arrays.fill(chars, 'u');

            final String bigBody = new String(chars);

            final CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);

            //CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", null);
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            RecordedRequest request = server.takeRequest();

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 2000)
    public void testFixedLengthUploadNoExpect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            final String bigBody = "Get this!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.start();
            server.enqueue(new MockResponse().setBody("Nice"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", null);
            conn.setFixedLengthStreamingMode(bigBody.length());
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);

            RecordedRequest request = server.takeRequest();

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 2000)
    public void testChunkedLengthUploadNoExpect() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("Nice"));

            char[] chars = new char[128000];
            Arrays.fill(chars, 'u');

            final String bigBody = new String(chars);

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", null);
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            RecordedRequest request = server.takeRequest();

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(conn.getResponseCode(), 200);

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 3000)
    public void testFixedLengthUploadExpectContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            final String bigBody = "Get this!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.start();
            server.enqueue(new MockResponse()
                    .setBody("Nice")
                    .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", "100-Continue");
            conn.setFixedLengthStreamingMode(bigBody.length());
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());

            RecordedRequest request = server.takeRequest();

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 3000)
    public void testFixedLengthUploadExpectContinueWithHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            final String bigBody = "Get this!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.start();
            server.enqueue(new MockResponse()
                    .setHeader("foobar", "Hooray!")
                    .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE));

            final CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", "100-Continue");
            conn.setFixedLengthStreamingMode(bigBody.length());
            conn.setUrlString(server.url("/").toString());

            conn.connect();

            assertEquals(100, conn.getResponseCode());
            assertEquals(0, conn.getHeaderFieldInt("content-length", -1));

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            //assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());
            assertEquals("Hooray!", conn.getHeaderField("FOOBAR"));

            RecordedRequest request = server.takeRequest();

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 3000)
    public void testChunkedLengthUploadExpectContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("Nice")
                    .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE));

            char[] chars = new char[128000];
            Arrays.fill(chars, 'u');

            final String bigBody = new String(chars);

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", "100-Continue");
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            RecordedRequest request = server.takeRequest();

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 4000)
    public void testFixedLengthUploadExpectNoContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            final String bigBody = "Get this!!!!!!!1!!!!!!!!!!!!!!!!2!!!!!!!!!!!!!!!!!!3!!!!!!!!!!4";

            server.start();
            server.enqueue(new MockResponse()
                    .setBody("Nice"));

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", "100-Continue");
            conn.setFixedLengthStreamingMode(bigBody.length());
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());

            RecordedRequest request = server.takeRequest();

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @Test(timeout = 4000)
    public void testChunkedLengthUploadExpectNoContinue() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("Nice"));

            char[] chars = new char[128000];
            Arrays.fill(chars, 'u');

            final String bigBody = new String(chars);

            CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
            conn.setDoOutput(true);
            conn.setRequestProperty("Expect", "100-Continue");
            conn.setUrlString(server.url("/").toString());

            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(bigBody.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            RecordedRequest request = server.takeRequest();

            assertEquals("Nice", convertStreamToString(conn.getInputStream()));
            assertEquals(200, conn.getResponseCode());

            assertEquals("POST", request.getMethod());
            assertEquals(bigBody, request.getBody().readUtf8());
        }
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }

        /*
    @Test public void post() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("abc"));

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(server.url("/"))
                    .post(RequestBody.create(MediaType.parse("text/plain"), "def"))
                    .build();

            executeSynchronously(server, client, request);

            RecordedRequest recordedRequest = server.takeRequest();
            assertEquals("POST", recordedRequest.getMethod());
            assertEquals("def", recordedRequest.getBody().readUtf8());
            assertEquals("3", recordedRequest.getHeader("Content-Length"));
            assertEquals("text/plain; charset=utf-8", recordedRequest.getHeader("Content-Type"));
        }
    }

    private RecordedResponse executeSynchronously(MockWebServer server, OkHttpClient client, String path, String... headers) throws IOException {
        Request.Builder builder = new Request.Builder();
        builder.url(server.url(path));
        for (int i = 0, size = headers.length; i < size; i += 2) {
            builder.addHeader(headers[i], headers[i + 1]);
        }
        return executeSynchronously(server, client, builder.build());
    }

    private RecordedResponse executeSynchronously(MockWebServer server, OkHttpClient client, Request request) throws IOException {
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            String bodyString = response.body().string();
            return new RecordedResponse(request, response, null, bodyString, null);
        } catch (IOException e) {
            return new RecordedResponse(request, null, null, null, e);
        }
    }
*/
}
