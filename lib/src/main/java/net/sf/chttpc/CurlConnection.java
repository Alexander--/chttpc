package net.sf.chttpc;

import android.os.Build;
import android.support.annotation.AnyThread;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.sf.xfd.Interruption;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.sql.Date;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.FutureTask;

public class CurlConnection extends HttpURLConnection {
    private static final int STATE_CLEAN = 0;
    private static final int STATE_DIRTY = 1;
    private static final int STATE_RECYCLED = 2;
    @SuppressWarnings("all")
    private static final String REQ_METHOD_DEFAULT = new String("GET");

    private final Config config;

    private HeaderMap headerMap;
    private CurlHttp curl;
    private int state;

    protected Proxy proxy;

    protected CurlConnection(@NonNull Config config) {
        super(null);

        this.config = config;
        this.curl = config.getCurl();

        this.method = REQ_METHOD_DEFAULT;
    }

    public void setUrlString(@NonNull String urlString) {
        assertNotConnected();

        final MutableUrl current = curl.getUrl();
        current.length = 0;
        current.append(urlString);
    }

    public void setProxy(@Nullable Proxy proxy) {
        assertNotConnected();

        this.proxy = proxy;
    }

    @NonNull
    @CheckResult
    public CurlHttp getCurl() {
        reacquire();

        return curl;
    }

    @Override
    @CheckResult
    public URL getURL() {
        if (state != STATE_RECYCLED) {
            try {
                url = new URL(curl.getUrl().toString());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        return url;
    }

    private void assertConnected() {
        if (!connected) {
            throw new IllegalStateException("Can not get headers before establishing connection");
        }
    }

    private void assertNotConnected() {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }

        reacquire();
    }

    @Override
    @AnyThread
    @CheckResult
    public String getHeaderFieldKey(int n) {
        assertConnected();

        return curl.getResponseHeaderKey(n);
    }

    @Override
    @AnyThread
    @CheckResult
    public String getHeaderField(int n) {
        assertConnected();

        return curl.getResponseHeader(n);
    }

    @Override
    @AnyThread
    @CheckResult
    public long getHeaderFieldLong(@NonNull String name, long Default) {
        assertConnected();

        return curl.getResponseHeader(name, Default);
    }

    @Override
    @AnyThread
    public int getHeaderFieldInt(@NonNull String name, int Default) {
        return (int) getHeaderFieldLong(name, Default);
    }

    @Override
    @AnyThread
    @CheckResult
    public String getHeaderField(@NonNull String name) {
        assertConnected();

        return curl.getResponseHeader(name);
    }

    @Override
    @AnyThread
    @CheckResult
    @SuppressWarnings("deprecation")
    public long getHeaderFieldDate(@NonNull String name, long Default) {
        final String value = getHeaderField(name);

        if (value == null) {
            return Default;
        }

        return Date.parse(value.contains("GMT") ? value : value + " GMT");
    }

    @Override
    @AnyThread
    @CheckResult
    @SuppressWarnings("unchecked")
    public @NonNull Map<String, List<String>> getHeaderFields() {
        assertConnected();

        HeaderMap headerMap = this.headerMap;

        if (headerMap != null) {
            return (Map) headerMap;
        }

        final String[] cachedHeaders = curl.getResponseHeaders();

        if (cachedHeaders == null || cachedHeaders.length == 0) {
            return Collections.emptyMap();
        }

        headerMap = HeaderMap.create(cachedHeaders.length);

        for (int i = 0; i < cachedHeaders.length; ++i) {
            final String headerName = cachedHeaders[i];

            if (headerName == null) {
                break;
            }

            int j = i + 2;
            while (j < cachedHeaders.length && cachedHeaders[j] != null) {
                ++j;
            }

            headerMap.append(headerName, cachedHeaders, i + 1, j);

            i = j;
        }

        return (Map) (this.headerMap = headerMap);
    }

    @Override
    public void connect() throws IOException {
        if (connected) {
            return;
        }

        final Interruption helper = Interruption.begin();
        try {
            if (helper.isInterrupted()) {
                throw new InterruptedIOException();
            }

            configure(helper);

            if (helper.isInterrupted()) {
                throw new InterruptedIOException();
            }
        } finally {
            Interruption.end();
        }

        this.connected = true;

        getResponseCode();
    }

    protected void configure(Interruption helper) throws IOException {
        reacquire();

        curl.configure(
                helper,
                getRequestMethod(),
                getProxyAddress(),
                config.getDnsServers(),
                getNetworkInterface(),
                getFixedLength(),
                getReadTimeout(),
                getConnectTimeout(),
                chunkLength,
                getProxyType(),
                getRequestType(),
                getInstanceFollowRedirects(),
                getDoInput(),
                getDoOutput());
    }

    @Override
    @CheckResult
    public boolean getInstanceFollowRedirects() {
        return instanceFollowRedirects;
    }

    @SuppressWarnings("StringEquality")
    private boolean usingDefaultMethod() {
        return method == REQ_METHOD_DEFAULT;
    }

    @Override
    public String getRequestMethod() {
        if (!usingDefaultMethod()) {
            return super.getRequestMethod();
        }

        if (doOutput) {
            return "POST";
        } else if (doInput) {
            return "GET";
        } else {
            return "HEAD";
        }
    }

    public void setRequestMethod(@Nullable String newMethod) throws ProtocolException {
        if (connected) {
            throw new ProtocolException("Can't reset method: already connected");
        }

        if (newMethod == null) {
            newMethod = "GET";
        }

        this.method = newMethod.toUpperCase(Locale.US);
    }

    @CurlProxy.ProxyType
    private int getProxyType() {
        if (proxy == null) {
            return CurlProxy.NONE;
        }

        return CurlProxy.getType(proxy);
    }

    @CurlHttp.Method
    private int getRequestType() {
        switch (getRequestMethod()) {
            case "GET":
                return CurlHttp.GET;
            case "POST":
                return CurlHttp.POST;
            case "PUT":
                return CurlHttp.PUT;
            case "HEAD":
                return CurlHttp.HEAD;
            default:
                return CurlHttp.CUSTOM;
        }
    }

    private long getFixedLength() {
        if (Build.VERSION.SDK_INT >= 19) {
            return Math.max((long) fixedContentLength, fixedContentLengthLong);
        } else {
            return fixedContentLength;
        }
    }

    @Override
    public void disconnect() {
        curl.clearHeaders();

        reset();

        state = STATE_CLEAN;
    }

    public void reset() {
        connected = false;
        responseCode = -1;

        final InputStream input = inputStream;
        if (input != null) {
            inputStream = null;

            try {
                input.close();
            } catch (IOException ignored) {
            }
        }

        final OutputStream output = outputStream;
        if (output != null) {
            outputStream = null;

            try {
                output.close();
            } catch (IOException ignored) {
            }
        }

        curl.reset();
    }

    private InputStream inputStream;

    @Override
    @AnyThread
    @CheckResult
    public InputStream getErrorStream() {
        if (!connected || responseCode < 400) {
            return null;
        }

        if (inputStream == null) {
            inputStream = curl.newInputStream();
        }

        return inputStream;
    }

    @Override
    @Nullable
    public String getResponseMessage() throws IOException {
        if (responseMessage != null && responseCode != 100) {
            return responseMessage;
        }

        connect();

        final String statusLine = getHeaderField(0);

        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            return null;
        }

        int codePos = statusLine.indexOf(' ');
        if (codePos == -1) {
            return null;
        }

        int phrasePos = statusLine.indexOf(' ', codePos + 1);
        if (phrasePos > 0 && phrasePos < statusLine.length()) {
            responseMessage = statusLine.substring(phrasePos + 1);
        }

        return responseMessage;
    }

    @Override
    public int getResponseCode() throws IOException {
        connect();

        if (responseCode > 0 && responseCode != 100) {
            return responseCode;
        }

        responseCode = curl.getResponseCode();

        return responseCode;
    }

    /**
     * @return {@link InputStream} of the connection
     */
    @Override
    @Deprecated
    public InputStream getContent() throws IOException {
        createInputStream();
        return inputStream;
    }

    /**
     * @deprecated this method always returns {@link InputStream}, of the connection or {@code null}
     */
    @Override
    @Deprecated
    public Object getContent(Class[] classes) throws IOException {
        for (Class aClass : classes) {
            if (InputStream.class.isAssignableFrom(aClass)) {
                createInputStream();
                return inputStream;
            }
        }

        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!doInput) {
            throw new ProtocolException("getInputStream can not be called when doInput = false");
        }

        createInputStream();

        if (responseCode >= 400) {
            throw new IOException("Server returned HTTP response code: " + responseCode + " for URL: " + curl.url);
        }

        return inputStream;
    }

    @SuppressWarnings("all")
    private void createInputStream() throws IOException {
        connect();

        if (inputStream == null) {
            inputStream = curl.newInputStream();
        }
    }

    private OutputStream outputStream;

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!doOutput) {
            throw new ProtocolException("getOutputStream can not be called when doOutput = false");
        }

        connect();

        if (outputStream == null) {
            outputStream = curl.newOutputStream();
        }

        return outputStream;
    }

    @Override
    @AnyThread
    public void setRequestProperty(@NonNull String key, String value) {
        assertNotConnected();

        curl.setHeaderField(key, value);
    }

    @Override
    @AnyThread
    public void addRequestProperty(@NonNull String key, @NonNull String value) {
        assertNotConnected();

        curl.addHeaderField(key, value);
    }

    @Override
    @AnyThread
    @CheckResult
    public String getRequestProperty(@NonNull String key) {
        assertNotConnected();

        return curl.getRequestHeader(key);
    }

    @Override
    @AnyThread
    @CheckResult
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getRequestProperties() {
        assertNotConnected();

        final String[] headerArray = curl.getRequestHeaders();

        if (headerArray == null) {
            return Collections.emptyMap();
        }

        final HeaderMap map = HeaderMap.create(headerArray.length);

        for (int i = 0; i < headerArray.length && headerArray[i] != null; i += 2) {
            final String key = headerArray[i];
            final String value = headerArray[i + 1];
            map.append(key, value);
        }

        return (Map) map;
    }

    @Override
    @AnyThread
    @CheckResult
    public boolean usingProxy() {
        return getProxyType() != CurlProxy.NONE;
    }

    private String getNetworkInterface() {
        return config.getNetworkInterface(curl.url);
    }

    private String getProxyAddress() {
        return proxy == null || !usingProxy() ? null : proxy.address().toString();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ":" + curl.getUrl();
    }

    /**
     * Attempt to obtain internal {@link CurlHttp} instance, detaching it from this CurlConnection
     * in process. This method will succeed only if this CurlConnection is freshly created or just
     * have had {@link #disconnect} executed.
     *
     * @return a ready-to-use {@link CurlHttp} instance or {@code null}, if the internal instance can not be reused
     */
    @Nullable
    protected CurlHttp recycle() {
        if (state != STATE_CLEAN) {
            return null;
        }

        this.state = STATE_RECYCLED;

        return curl;
    }

    private void reacquire() {
        if (state == STATE_RECYCLED) {
            curl = config.getCurl();
        }

        state = STATE_DIRTY;
    }

    public interface Config {
        @NonNull
        CurlHttp getCurl();

        @Nullable
        String getDnsServers();

        @Nullable
        String getNetworkInterface(@NonNull MutableUrl url);

        @Nullable
        Proxy getProxy(@NonNull MutableUrl url);
    }
}
