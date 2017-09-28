package net.sf.chttpc;

import android.os.ParcelFileDescriptor;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import net.sf.chttpc.test.BaseTestSuite;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SocketRetrievalTests extends BaseTestSuite {
    // minimal heap size is 16MB, so...
    private static final int HUGE_BUFFER = 10 * 1024 * 1024;

    @BeforeClass
    public static void setupCleaner() throws Exception {
        baseSetup();
    }

    @AfterClass
    public static void cleanup() throws IOException {
        baseTeardown();
    }

    @Test
    public void testSendfile() throws IOException, ErrnoException, InterruptedException {
        File f = File.createTempFile("wgwehweh", "wgwehwehw");

        try {
            final SocketChannel throwaway = SocketChannel.open();
            throwaway.configureBlocking(false);

            try (ParcelFileDescriptor inFd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE);
                 InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(inFd);
                 OutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(inFd)) {

                populateFromRandom(outputStream);

                Os.lseek(inFd.getFileDescriptor(), 0, OsConstants.SEEK_SET);

                try (final MockWebServer server = new MockWebServer()) {
                    server.enqueue(new MockResponse()
                            .setResponseCode(200)
                            .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE)
                            .setBody("Gee!"));

                    CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
                    conn.setFixedLengthStreamingMode(HUGE_BUFFER);
                    conn.setUrlString(server.url("/").toString());
                    conn.setDoOutput(true);
                    conn.connect();

                    try (ParcelFileDescriptor outFd = ParcelFileDescriptor.fromSocket(throwaway.socket())) {
                        conn.getCurl().getFileDescriptor(outFd.getFd());

                        throwaway.configureBlocking(true);
                        try {
                            int sent = 0;

                            while (sent < HUGE_BUFFER) {
                                sent += Os.sendfile(outFd.getFileDescriptor(), inFd.getFileDescriptor(), null, HUGE_BUFFER - sent);
                            }
                        } finally {
                            throwaway.configureBlocking(false);
                        }
                    }

                    Os.lseek(inFd.getFileDescriptor(), 0, OsConstants.SEEK_SET);

                    assertTrue(isEqual(inputStream, server.takeRequest().getBody().inputStream()));
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private void populateFromRandom(OutputStream outputStream) throws IOException {
        new Buffer().readFrom(random, HUGE_BUFFER).writeTo(outputStream);
    }
}
