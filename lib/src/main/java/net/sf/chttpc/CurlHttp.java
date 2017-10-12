package net.sf.chttpc;

import android.support.annotation.CheckResult;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.system.Os;

import net.sf.xfd.Interruption;
import net.sf.xfd.NativePeer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.ReferenceQueue;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

@NativePeer
public class CurlHttp {
    public static final String DEBUG = "net.sf.chttpc.debug";

    private static boolean debug;

    static {
        System.loadLibrary("chttpc-" + BuildConfig.NATIVE_VER);

        Curl.nativeInit();

        debug = Boolean.valueOf(System.getProperty(DEBUG));
    }

    public static final int GET = 0;
    public static final int POST = 1;
    public static final int PUT = 2;
    public static final int HEAD = 3;
    public static final int CUSTOM = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({GET, POST, PUT, HEAD, CUSTOM})
    public @interface Method {}

    public static final int FLAG_ENABLE_DEBUG  =    1;
    public static final int FLAG_SEND_AFTER_ERROR = 1 << 1;
    public static final int FLAG_TCP_KEEP_ALIVE =   1 << 2;
    public static final int FLAG_USE_FALSE_START =  1 << 3;
    public static final int FLAG_USE_FAST_OPEN =    1 << 4;
    public static final int FLAG_RAW_RESPONSE =     1 << 5;
    public static final int FLAG_TCP_NODELAY =      1 << 6;
    public static final int FLAG_USE_IPV6 =         1 << 7;
    public static final int FLAG_SEND_TE_HEADER =   1 << 8;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            FLAG_ENABLE_DEBUG,
            FLAG_SEND_AFTER_ERROR,
            FLAG_TCP_KEEP_ALIVE,
            FLAG_USE_FALSE_START,
            FLAG_USE_FAST_OPEN,
            FLAG_RAW_RESPONSE,
            FLAG_TCP_NODELAY,
            FLAG_USE_IPV6,
            FLAG_SEND_TE_HEADER,
    })
    public @interface Flags {}

    @Flags
    public static final int DEFAULT_FLAGS = FLAG_USE_IPV6 | FLAG_TCP_NODELAY;

    private static final int OPTION_TCP_KEEPALIVE_PERIOD  = 0;
    private static final int OPTION_100_CONTINUE_TIMEOUT  = 1;
    private static final int OPTION_CONNECTIONS_IN_CACHE  = 2;
    private static final int OPTION_MAX_REDIRECT_COUNT  = 3;

    protected final MutableUrl url;
    protected final long curlPtr;

    /**
     * Creates and initializes a new curl handle. The native handles, created by this constructor,
     * do not get garbage-collected automatically, — you must ensure, that associated
     * native resources are released by using {@link CleanerRef} or via other means. Failure
     * to do so will result in memory leaks and file descriptor leaks.
     *
     * <p/>
     *
     * The created handle must be set up with target URL and other parameters, by calling
     * {@link #configure}.
     */
    protected CurlHttp(MutableUrl url, @Flags int flags) {
        this.url = url;

        this.curlPtr = Curl.nativeCreate(flags);
    }

    protected static CurlHttp create(@NonNull ReferenceQueue<Object> refQueue) {
        int flags = DEFAULT_FLAGS;

        if (debug) {
            flags |= FLAG_ENABLE_DEBUG;
        }

        return create(refQueue, flags);
    }

    protected static CurlHttp create(@NonNull ReferenceQueue<Object> refQueue, int flags) {
        final CurlHttp curl = new CurlHttp(new MutableUrl(), flags);

        CleanerRef.create(curl, curl.curlPtr, refQueue);

        return curl;
    }

    @NonNull
    @CheckResult
    public MutableUrl getUrl() {
        return url;
    }

    /**
     * Set how long to wait for server response when sending "Expect: 100-Continue" header.
     */
    public void setExpectContinueTimeout(long timeout, @NonNull TimeUnit unit) {
        Curl.setOptionInt(curlPtr, unit.toMillis(timeout), OPTION_100_CONTINUE_TIMEOUT);
    }

    /**
     * Set period between sending TCP keep-alive probes. The probes are sent by OS automatically
     * even after inactive connection is placed in connection cache. Note, that TCP keep-alive
     * can keep network adapter in active state, when it would otherwise enter energy-saving sleep.
     *
     * <p/>
     *
     * Can not be set to values smaller than one second.
     *
     * @see #setConnectionCacheSize(int)
     */
    public void setTcpKeepAlivePeriod(long timeout, @NonNull TimeUnit unit) {
        final long seconds = unit.toSeconds(timeout);

        Curl.setOptionInt(curlPtr, seconds > 0 ? seconds : 1, OPTION_TCP_KEEPALIVE_PERIOD);
    }

    /**
     * Change size of internal cache, used for storing connections for reuse. The caches are local
     * to each {@link CurlHttp} instance.
     */
    public void setConnectionCacheSize(int size) {
        Curl.setOptionInt(curlPtr, size, OPTION_CONNECTIONS_IN_CACHE);
    }

    /**
     * Set maximum number of HTTP redirects during single request. Set to 0 to disable the limit.
     * Default: 0.
     */
    public void setMaxRedirects(int count) {
        Curl.setOptionInt(curlPtr, count, OPTION_MAX_REDIRECT_COUNT);
    }

    /**
     * Set the header with specified name to specified value. This method does not perform any
     * validation of provided header.
     *
     * If you pass {@code null} to the second parameter, all headers with specified name
     * will be removed. If the specified header has already been added multiple times via
     * {@link #addHeaderField}, only the last one will be changed.
     */
    public void setHeaderField(@NonNull String key, @Nullable String value) {
        Curl.setHeader(curlPtr, key, value, key.length(), value == null ? -1 : value.length());
    }

    /**
     * Add a new request header with specified name and value. This method does not perform any
     * validation of provided header.
     *
     * Note, that according to HTTP specification, multiple headers with same name are permitted
     * only when their values can consist of comma-separated lists (e.g. "Accept", "Cookie" and
     * similar headers).
     */
    public void addHeaderField(@NonNull String key, @NonNull String value) {
        Curl.addHeader(curlPtr, key, value, key.length(), value.length());
    }

    /**
     * Remove all previously set request headers. If you reuse curl instance without calling this
     * method, all previously set headers will be submitted with following requests. This method
     * is independent from {@link #reset}.
     */
    public void clearHeaders() {
        Curl.clearHeaders(curlPtr);
    }

    /**
     * Returns value of previously set request header. If multiple headers with specified name
     * were added via {@link #addHeaderField}, only value of the last one will be returned.
     *
     * <p/>
     *
     * This method is provided for compatibility with {@link URLConnection#getRequestProperty}.
     * If you need to perform complex manipulations with request headers before sending the request,
     * you are advised to use a separate specialized structure, such as {@link LinkedHashMap} or
     * multimap.
     */
    @CheckResult
    public String getRequestHeader(@NonNull String key) {
        return Curl.outHeader(curlPtr, key, key.length());
    }

    /**
     * Returns array with serialized representation of request headers:
     *
     * <pre>{@code
     *
     *   0      1      2      3      4      5      6      7      8      9
     *   |      |      |      |      |      |      |      |      |      |
     * name1  value1 name1  value2 name2  value  name3  value1 name3  value2
     *
     * }</pre>
     *
     * @return request headers in order of addition or {@code null}, if no headers were added yet
     */
    @Nullable
    @CheckResult
    protected String[] getRequestHeaders() {
        return Curl.getHeaders(curlPtr, false);
    }

    /**
     * Returns array, with serialized representation of response headers:
     *
     * <pre>{@code
     *
     *   0      1      2      3      4      5      6      7      8      9
     *   |      |      |      |      |      |      |      |      |      |
     * name1  value1 value2  null  name2  value   null  name3  value1  value2
     *
     * }</pre>
     *
     * The order is preserved with respect to HTTP spec, but it is not necessarily same as order
     * of arrival: headers with same name are grouped together to simplify multimap initialization.
     *
     * @return array with response headers or {@code null} if none has arrived
     */
    @Nullable
    @CheckResult
    protected String[] getResponseHeaders() {
        return Curl.getHeaders(curlPtr, true);
    }

    /**
     * Returns value of response header at specified position. Position 0 can be used to access
     * HTTP status line. Unlike order of elements, returned by {@link #getRequestHeaders},
     * the headers are accessed by this method in exact same order, in which they arrived from server.
     *
     * <p/>
     *
     * Note, that HTTP 2.0 does not have status line in the same sense as HTTP 1.x, so value,
     * returned by call to {@code getResponseHeader(0)}, might be synthetic except for status code:
     * the HTTP version might be set to wrong value and no status phrase may be present.
     *
     * @param pos header position
     *
     * @return header value or {@code null} if {@code pos} is 0 or bigger than total header count
     */
    @CheckResult
    public String getResponseHeader(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException();
        }

        return Curl.header(curlPtr, null, pos);
    }

    /**
     * Returns name of response header at specified position. Position 0 is reserved for HTTP
     * status line, so it's name will always be {@code null}. Unlike order of elements, returned
     * by {@link #getRequestHeaders}, the headers are accessed by this method in exact same order,
     * in which they arrived from server.
     *
     * @param pos header position
     *
     * @return header value or {@code null} if {@code pos} is 0 or bigger than total header count
     */
    @CheckResult
    public String getResponseHeaderKey(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException();
        }

        return pos == 0 ? null : Curl.header(curlPtr, null, -pos);
    }

    /**
     * Returns value of response header with provided name.
     *
     * @param key header name, case-insensitive
     *
     * @return header value or {@code null} if header with specified name does not exist
     */
    @CheckResult
    public String getResponseHeader(@NonNull String key) {
        return Curl.header(curlPtr, key, key.length());
    }

    /**
     * @param key header name, case-insensitive
     * @param defaultValue value to return if the header does not exist or can not be parsed
     */
    @CheckResult
    public long getResponseHeader(@NonNull String key, long defaultValue) {

        return Curl.intHeader(curlPtr, defaultValue, key, key.length());
    }

    @CheckResult
    public int getResponseCode() {
        return (int) Curl.intHeader(curlPtr, 0, null, 0);
    }

    /**
     * Set various parameters, that affect processing of request by curl.
     *
     * <p/>
     *
     * You must call this method exactly once before examining response headers or using streams,
     * returned by {@link #newInputStream} and {@link #newOutputStream}.
     *
     * <p/>
     *
     * Before calling this method again, you have to call {@link #reset} and, optionally,
     * {@link #clearHeaders}.
     */
    public void configure(
            @NonNull Interruption i,
            @NonNull String method,
            @Nullable String proxy,
            @Nullable String dns,
            @Nullable String ifName,
            long contentLength,
            int readTimeout,
            int connectTimeout,
            int chunkSize,
            @CurlProxy.ProxyType int proxyType,
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
                i.toNative(),
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
        Curl.reset(curlPtr);
    }

    @NonNull
    public OutputStream newOutputStream() {
        return new CurlOutputStream();
    }

    @NonNull
    public InputStream newInputStream() {
        return new CurlInputStream();
    }

    /**
     * Obtain the file descriptor of socket, used for the last in-progress transfer.
     * Calling this method before the response code is retrieved can produce
     * surprising results (such as returning file descriptor of socket, internally
     * used for DNS resolution). Note, that in case of HTTPS transfers the descriptor
     * can not be used to directly write data (because the data has to be encrypted
     * according to TLS rules), but it is still possible to set various socket parameters
     * on it. Reading from the descriptor is generally a bad idea even with plain HTTP,
     * because of internal buffering, used by curl when parsing server response.
     *
     * <p/>
     *
     * Since the descriptor is returned via {@code dup}-ing it into the user-provided descriptor,
     * curl won't close it, you are responsible for closing supplied descriptor after you are
     * done with it. Nevertheless, performing state-affecting calls such as {@link Os#shutdown} on
     * returned descriptor may be a bad idea, because the actual connection is still managed by
     * curl's connection cache.
     *
     * @param fd the throwaway descriptor, used for {@code dup2} invocation
     * @throws IOException if there are no in-progress transfers or the descriptor isn't available
     */
    public void getFileDescriptor(int fd) throws IOException {
        Curl.getLastFd(curlPtr, fd);
    }

    /**
     * Deallocate native resources, associated with specified handle.
     *
     * Do not use. There is generally no safe way to invoke this method, except after the instance
     * has been garbage-collected.
     */
    protected static void nativeDispose(long nativePtr) {
        Curl.dispose(nativePtr);
    }

    protected int httpRead(Interruption i, byte[] buffer, int off, int count) throws IOException {
        return Curl.read(curlPtr, i.toNative(), buffer, off, count);
    }

    protected int httpWrite(Interruption i, byte[] buffer, int off, int count) throws IOException {
        return Curl.write(curlPtr, i.toNative(), buffer, off, count);
    }

    protected void httpWriteEnd(Interruption i) throws IOException {
        Curl.closeOutput(curlPtr, i.toNative());
    }

    private static void i10nCheck(Interruption i10n, int transferred) throws InterruptedIOException {
        if (!i10n.isInterrupted()) return;

        Thread.interrupted();

        final InterruptedIOException exception = new InterruptedIOException();

        exception.bytesTransferred = transferred;

        throw exception;
    }

    private final class CurlInputStream extends InputStream {
        private boolean closed;

        CurlInputStream() {
        }

        @Override
        public int read() throws IOException {
            return doRead(null, 0, 1);
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

        private int doRead(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }

            final Interruption helper = Interruption.begin();
            try {
                i10nCheck(helper, 0);

                int read = httpRead(helper, b, off, len);

                i10nCheck(helper, read);

                return read;
            } finally {
                Interruption.end();
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final class CurlOutputStream extends OutputStream {
        private boolean closed;

        CurlOutputStream() {
        }

        @Override
        public void write(int b) throws IOException {
            doWrite(null, b, 1);
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

        private void doWrite(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }

            final Interruption helper = Interruption.begin();
            try {
                i10nCheck(helper, 0);

                i10nCheck(helper, httpWrite(helper, b, off, len));
            } finally {
                Interruption.end();
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;

            closed = true;

            final Interruption helper = Interruption.begin();
            try {
                i10nCheck(helper, 0);

                httpWriteEnd(helper);

                i10nCheck(helper, 0);
            } finally {
                Interruption.end();
            }
        }
    }
}
