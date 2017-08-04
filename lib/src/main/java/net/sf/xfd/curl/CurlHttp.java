package net.sf.xfd.curl;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.sf.xfd.curl.CurlProxy.ProxyType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.ReferenceQueue;

public class CurlHttp {
    static boolean debug;

    static {
        System.loadLibrary("curl-wrapper");

        Curl.nativeInit();

        debug = Boolean.valueOf(System.getProperty("net.sf.chttpc.debug"));
    }

    public static final int GET = 0;
    public static final int POST = 1;
    public static final int PUT = 2;
    public static final int HEAD = 3;
    public static final int CUSTOM = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({GET, POST, PUT, HEAD, CUSTOM})
    public @interface Method {}

    protected final MutableUrl url = new MutableUrl();

    protected final long curlPtr;

    protected CurlHttp(boolean enableDebug,
                       boolean enableSendingAfterError,
                       boolean enableTcpKeepAlive,
                       boolean enableFalseStart,
                       boolean enableFastOpen) {
        this.curlPtr = Curl.nativeCreate(
                enableDebug,
                enableSendingAfterError,
                enableTcpKeepAlive,
                enableFalseStart,
                enableFastOpen
        );
    }

    @NonNull
    public static CurlHttp create(@NonNull ReferenceQueue<Object> refQueue) {
        final CurlHttp curl = new CurlHttp(debug, false, false, false, false);

        CleanerRef.create(curl, curl.curlPtr, refQueue);

        return curl;
    }

    @NonNull
    public MutableUrl getUrl() {
        return url;
    }

    public void clearRequestProperties() {
        Curl.clearHeaders(curlPtr);
    }

    public void setHeaderField(@NonNull String key, @Nullable String value) {
        Curl.setHeader(curlPtr, key, value, key.length(), value == null ? 0 : value.length());
    }

    public void addHeaderField(@NonNull String key, @NonNull String value) {
        Curl.addHeader(curlPtr, key, value, key.length(), value.length());
    }

    public String getRequestProperty(@NonNull String key) {
        return Curl.outHeader(curlPtr, key, key.length());
    }

    public String getHeaderField(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException();
        }

        return Curl.header(curlPtr, null, pos);
    }

    public String getHeaderKey(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException();
        }

        return pos == 0 ? null : Curl.header(curlPtr, null, -pos);
    }

    public String getHeaderField(@NonNull String key) {
        return Curl.header(curlPtr, key, key.length());
    }

    public long getHeaderFieldLong(@NonNull String key, long defaultValue) {
        return Curl.intHeader(curlPtr, defaultValue, key, key.length());
    }

    @Nullable
    public String[] getResponseHeaders() {
        return Curl.getHeaders(curlPtr, true);
    }

    @Nullable
    public String[] getRequestHeaders() {
        return Curl.getHeaders(curlPtr, false);
    }

    public void configure(
            @Nullable String method,
            @Nullable String proxy,
            @Nullable String dns,
            @Nullable String ifName,
            long contentLength,
            int readTimeout,
            int connectTimeout,
            int chunkSize,
            @ProxyType int proxyType,
            @Method int requestMethod,
            boolean followRedirects,
            boolean doInput,
            boolean doOutput) throws IOException {
        final char[] urlBuffer = url.buffer;
        final int urlLength = url.length;

        if (urlLength < 0 || urlLength > urlBuffer.length) {
            throw new IndexOutOfBoundsException();
        }

        char[] newUrl = Curl.nativeConfigure(
                curlPtr,
                contentLength,
                urlBuffer,
                method,
                proxy,
                dns,
                ifName,
                urlLength,
                readTimeout,
                connectTimeout,
                proxyType,
                requestMethod,
                chunkSize,
                followRedirects,
                doInput,
                doOutput);

        if (followRedirects && newUrl != null) {
            updateUrl(newUrl);
        }
    }

    private void updateUrl(char[] newUrl) {
        if (newUrl == url.buffer) {
            for (int i = 0; i < newUrl.length; ++i) {
                if (newUrl[i] == '\0') {
                    url.length = i;
                    return;
                }
            }
        } else {
            url.buffer = newUrl;
        }

        url.length = newUrl.length;
    }

    public void reset() {
        Curl.nativeReset(curlPtr);
    }

    @NonNull
    public OutputStream newOutputStream() {
        return new CurlOutputStream();
    }

    @NonNull
    public InputStream newInputStream() {
        return new CurlInputStream();
    }

    private final class CurlInputStream extends InputStream {
        private boolean closed;

        private CurlInputStream() {
        }

        @Override
        public int read() throws IOException {
            //Log.i("!!!", "READ SINGLE");

            if (closed) {
                throw new IOException("Already closed");
            }

            return Curl.nativeRead(curlPtr, null, 0, 1);
        }

        @Override
        public int read(@NonNull byte[] b) throws IOException {
            return doRead(b, 0, b.length);
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }

            return doRead(b, off, len);
        }

        private int doRead(@NonNull byte[] b, int off, int len) throws IOException {
            //Log.i("!!!", "READ MANY");

            if (closed) {
                throw new IOException("Already closed");
            }

            return Curl.nativeRead(curlPtr, b, off, len);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final class CurlOutputStream extends OutputStream {
        private boolean closed;

        private CurlOutputStream() {
        }

        @Override
        public void write(int b) throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }

            Curl.nativeWrite(curlPtr, null, b, 1);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || off > b.length - len) {
                throw new IndexOutOfBoundsException();
            }

            doWrite(b, off, len);
        }

        @Override
        public void write(@NonNull byte[] b) throws IOException {
            doWrite(b, 0, b.length);
        }

        private void doWrite(@NonNull byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }

            if (len == 0) {
                return;
            }

            Curl.nativeWrite(curlPtr, b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (closed) return;

            closed = true;

            Curl.nativeCloseOutput(curlPtr);
        }
    }
}
