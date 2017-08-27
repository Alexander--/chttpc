package net.sf.chttpc;

import android.os.ParcelFileDescriptor;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class SocketRetrievalTests extends BaseTestSuite {
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
            byte[] randomPayload = new byte[16 * 1024 * 1024];

            random.read(randomPayload);

            ByteBuffer payloadBuffer = ByteBuffer.wrap(randomPayload);

            Buffer bufferWithBody = new Buffer().write(randomPayload);

            final SocketChannel throwaway = SocketChannel.open();
            throwaway.configureBlocking(false);

            try (ParcelFileDescriptor inFd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE)) {
                int written = 0;

                while (written < randomPayload.length) {
                    written += Os.write(inFd.getFileDescriptor(), payloadBuffer);
                }

                Os.lseek(inFd.getFileDescriptor(), 0, OsConstants.SEEK_SET);

                try (final MockWebServer server = new MockWebServer()) {
                    server.enqueue(new MockResponse()
                            .setResponseCode(200)
                            .setSocketPolicy(SocketPolicy.EXPECT_CONTINUE)
                            .setBody("Gee!"));

                    CurlConnection conn = new CurlConnection(CurlHttp.create(queue), config);
                    conn.setFixedLengthStreamingMode(randomPayload.length);
                    conn.setUrlString(server.url("/").toString());
                    conn.setDoOutput(true);
                    conn.connect();

                    try (ParcelFileDescriptor outFd = ParcelFileDescriptor.fromSocket(throwaway.socket())) {
                        conn.getCurl().getFileDescriptor(outFd.getFd());

                        throwaway.configureBlocking(true);
                        try {
                            int sent = 0;

                            while (sent < randomPayload.length) {

                                sent += Os.sendfile(outFd.getFileDescriptor(), inFd.getFileDescriptor(), null, randomPayload.length);
                            }
                        } finally {
                            throwaway.configureBlocking(false);
                        }
                    }


                    assertEquals(bufferWithBody, server.takeRequest().getBody());
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
