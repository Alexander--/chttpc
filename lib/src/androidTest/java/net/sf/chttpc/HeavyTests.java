package net.sf.chttpc;

import android.support.test.runner.AndroidJUnit4;

import com.google.common.truth.Truth;
import com.koushikdutta.async.AsyncServerSocket;

import net.sf.chttpc.test.BaseTestSuite;
import net.sf.chttpc.test.StreamingUploadServer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

import okio.HashingSource;
import okio.Okio;

@RunWith(AndroidJUnit4.class)
public class HeavyTests extends BaseTestSuite {
    private static final int HUGE_BUFFER = 1024 * 1024 * 60;

    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @Test
    public void downloadHugeFixedLength() throws IOException, NoSuchAlgorithmException, InterruptedException {
        try (StreamingUploadServer sas = new StreamingUploadServer(HUGE_BUFFER)) {
            AsyncServerSocket sock = sas.listen(0);

            CurlHttp curl = CurlHttp.create(queue, CurlHttp.DEFAULT_FLAGS);

            CurlConnection conn = new CurlConnection(curl, config);
            conn.setUrlString("http://localhost:" + sock.getLocalPort() + "/fixed");
            conn.connect();

            Truth.assertThat(conn.getResponseCode()).isEqualTo(200);

            try (HashingSource source = HashingSource.sha256(Okio.source(conn.getInputStream()))) {
                Okio.buffer(source).readAll(Okio.blackhole());

                Truth.assertThat(source.hash()).isEqualTo(sas.digest());
            }
        }
    }

    @Test
    public void downloadHugeChunked() throws IOException, NoSuchAlgorithmException, InterruptedException {
        try (StreamingUploadServer sas = new StreamingUploadServer(HUGE_BUFFER)) {
            AsyncServerSocket sock = sas.listen(0);

            CurlHttp curl = CurlHttp.create(queue, CurlHttp.DEFAULT_FLAGS);

            CurlConnection conn = new CurlConnection(curl, config);
            conn.setUrlString("http://localhost:" + sock.getLocalPort() + "/chunked");
            conn.connect();

            Truth.assertThat(conn.getResponseCode()).isEqualTo(200);

            try (HashingSource source = HashingSource.sha256(Okio.source(conn.getInputStream()))) {
                Okio.buffer(source).readAll(Okio.blackhole());

                Truth.assertThat(source.hash()).isEqualTo(sas.digest());
            }
        }
    }

    @Test
    public void uploadHugeFixedLength() throws IOException, NoSuchAlgorithmException, InterruptedException {
        try (StreamingUploadServer sas = new StreamingUploadServer(HUGE_BUFFER)) {
            AsyncServerSocket sock = sas.listen(0);

            HashingSource data = HashingSource.sha256(Okio.source(random));

            CurlHttp curl = CurlHttp.create(queue, CurlHttp.DEFAULT_FLAGS);

            CurlConnection conn = new CurlConnection(curl, config);
            conn.setFixedLengthStreamingMode(HUGE_BUFFER);
            conn.setUrlString("http://localhost:" + sock.getLocalPort());
            conn.setRequestProperty("Content-Type", "application/binary");
            conn.setRequestProperty("Expect", null);
            conn.setDoOutput(true);
            conn.connect();

            try (OutputStream httpStream = conn.getOutputStream()) {
                Okio.buffer(Okio.sink(httpStream))
                        .write(data, HUGE_BUFFER)
                        .flush();
            }

            Truth.assertThat(sas.digest()).isEqualTo(data.hash());

            Truth.assertThat(conn.getResponseCode()).isEqualTo(200);
        }
    }

    @Test
    public void uploadHugeChunked() throws IOException, NoSuchAlgorithmException, InterruptedException {
        try (StreamingUploadServer sas = new StreamingUploadServer(HUGE_BUFFER)) {
            AsyncServerSocket sock = sas.listen(0);

            HashingSource data = HashingSource.sha256(Okio.source(random));

            CurlHttp curl = CurlHttp.create(queue, CurlHttp.DEFAULT_FLAGS);

            CurlConnection conn = new CurlConnection(curl, config);
            conn.setChunkedStreamingMode(400);
            conn.setUrlString("http://localhost:" + sock.getLocalPort());
            conn.setRequestProperty("Content-Type", "application/binary");
            conn.setRequestProperty("Expect", null);
            conn.setDoOutput(true);
            conn.connect();

            try (OutputStream httpStream = conn.getOutputStream()) {
                Okio.buffer(Okio.sink(httpStream))
                        .write(data, HUGE_BUFFER)
                        .flush();
            }

            Truth.assertThat(sas.digest()).isEqualTo(data.hash());

            Truth.assertThat(conn.getResponseCode()).isEqualTo(200);
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        baseTeardown();
    }
}
