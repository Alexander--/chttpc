package net.sf.chttpc.test;

import android.support.annotation.NonNull;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.koushikdutta.async.http.server.UnknownRequestBody;
import com.koushikdutta.async.stream.OutputStreamDataCallback;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;

import okio.ByteString;
import okio.Okio;

public final class StreamingUploadServer extends AsyncHttpServer implements Closeable {
    private final MessageDigest digest = MessageDigest.getInstance("SHA-256");

    private final DigestOutputStream digester =
            new DigestOutputStream(Okio.buffer(Okio.blackhole()).outputStream(), this.digest);

    private FileInputStream random = new FileInputStream("/dev/urandom");

    private final CountDownLatch sync = new CountDownLatch(1);

    private int sent;

    public StreamingUploadServer(final int total) throws NoSuchAlgorithmException, FileNotFoundException {
        addAction("GET", "/fixed", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.code(200);
                response.getHeaders().set("Content-Length", String.valueOf(total));
                response.sendStream(new DigestInputStream(random, digest), total);
                sync.countDown();
            }
        });

        addAction("GET", "/chunked", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                response.code(200);
                response.getHeaders().set("Transfer-Encoding", "Chunked");
                response.writeHead();
                response.sendStream(new DigestInputStream(random, digest) {
                    @Override
                    public void close() throws IOException {
                        response.end();
                    }
                }, total);
                sync.countDown();
            }
        });

        addAction("POST", "(.+)", new HttpServerRequestCallback() {
            @Override
            public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                final UnknownRequestBody body = (UnknownRequestBody) request.getBody();

                final DataEmitter emitter = body.getEmitter();

                emitter.setDataCallback(new OutputStreamDataCallback(digester) {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        sent += bb.remaining();

                        super.onDataAvailable(emitter, bb);

                        if (sent >= total) {
                            sync.countDown();
                        }
                    }
                });

                response.code(200);
                response.end();
            }
        });
    }

    public ByteString digest() throws IOException, InterruptedException {
        sync.await();
        random.close();
        digester.flush();
        digester.close();
        return ByteString.of(digest.digest());
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
