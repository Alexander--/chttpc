package net.sf.xfd.curl;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CurlConnection extends HttpURLConnection {
    private final CurlHttp curl;
    private final Config config;

    private Map<String, List<String>> headerMap;

    protected Proxy proxy;

    public CurlConnection(@NonNull CurlHttp curl, @NonNull Config config) {
        super(null);

        this.config = config;
        this.curl = curl;
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
    public CurlHttp getCurl() {
        return curl;
    }

    @Override
    public URL getURL() {
        try {
            return new URL(curl.getUrl().toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
    }

    @Override
    public String getHeaderFieldKey(int n) {
        assertConnected();

        return curl.getHeaderKey(n);
    }

    @Override
    public String getHeaderField(int n) {
        assertConnected();

        return curl.getHeaderField(n);
    }

    @Override
    public long getHeaderFieldLong(@NonNull String name, long Default) {
        assertConnected();

        return curl.getHeaderFieldLong(name, Default);
    }

    @Override
    public int getHeaderFieldInt(@NonNull String name, int Default) {
        return (int) getHeaderFieldLong(name, Default);
    }

    @Override
    public String getHeaderField(@NonNull String name) {
        assertConnected();

        return curl.getHeaderField(name);
    }

    @Override
    @SuppressWarnings("deprecation")
    public long getHeaderFieldDate(@NonNull String name, long Default) {
        final String value = getHeaderField(name);

        if (value == null) {
            return Default;
        }

        return Date.parse(value.contains("GMT") ? value : value + " GMT");
    }

    @Override
    public @NonNull Map<String, List<String>> getHeaderFields() {
        assertConnected();

        Map<String, List<String>> headerMap = this.headerMap;

        if (headerMap != null) {
            return headerMap;
        }

        final String[] cachedHeaders = curl.getResponseHeaders();

        if (cachedHeaders == null || cachedHeaders.length == 0 || cachedHeaders.length == 1) {
            return Collections.emptyMap();
        }

        final List<String> headerList = Arrays.asList(cachedHeaders);

        headerMap = new HashMap<>(cachedHeaders.length);

        for (int i = 0; i < cachedHeaders.length; ++i) {
            final String headerName = cachedHeaders[i];

            int j = i + 2;
            while (j < cachedHeaders.length && cachedHeaders[j] != null) {
                ++j;
            }

            final List<String> headerValues;
            if (j == i + 2) {
                headerValues = Collections.singletonList(cachedHeaders[i + 1]);
            } else {
                headerValues = headerList.subList(i + 1, j);
            }

            headerMap.put(headerName, headerValues);

            i = j;
        }

        return this.headerMap = headerMap;
    }

    @Override
    public void connect() throws IOException {
        if (connected) {
            return;
        }

        curl.configure(
                getRequestMethod(),
                getProxyAddress(),
                config.getDnsServers(),
                config.getNetworkInterface(),
                getFixedLength(),
                getReadTimeout(),
                getConnectTimeout(),
                chunkLength,
                getProxyType(),
                getRequestType(),
                getInstanceFollowRedirects(),
                getDoInput(),
                getDoOutput());

        this.connected = true;
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return instanceFollowRedirects;
    }

    public void setRequestMethod(@Nullable String newMethod) throws ProtocolException {
        if (connected) {
            throw new ProtocolException("Can't reset method: already connected");
        }

        if (newMethod == null) {
            newMethod = "GET";
        }

        this.method = newMethod.toUpperCase(Locale.US);

        switch (newMethod) {
            case "HEAD":
                doInput = false;
                doOutput = false;
                break;
            case "PUT":
            case "POST":
                doOutput = true;
        }
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.doOutput = doOutput;

        if (doOutput) {
            switch (method) {
                case "HEAD":
                case "GET":
                    method = "POST";
            }
        }
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
    public InputStream getErrorStream() {
        if (!doInput) {
            throw new IllegalStateException("getErrorStream can not be called when doInput = false");
        }

        if (!connected || responseCode >= 0 && responseCode < 400) {
            return null;
        }

        if (inputStream == null) {
            inputStream = curl.newInputStream();
        }

        return inputStream;
    }

    @Override
    public String getResponseMessage() throws IOException {
        getResponseCode();

        return responseMessage;
    }

    @Override
    public int getResponseCode() throws IOException {
        if (responseCode > 0) {
            return responseCode;
        }

        createInputStream();

        final String statusLine = getHeaderField(0);

        if (!statusLine.startsWith("HTTP/")) {
            return -1;
        }

        int codePos = statusLine.indexOf(' ');
        if (codePos == -1) {
            return -1;
        }

        int phrasePos = statusLine.indexOf(' ', codePos + 1);
        if (phrasePos > 0 && phrasePos < statusLine.length()) {
            responseMessage = statusLine.substring(phrasePos + 1);
        }

        // deviation from RFC 2616 - don't reject status line
        // if SP Reason-Phrase is not included.
        if (phrasePos < 0) {
            phrasePos = statusLine.length();
        }

        try {
            responseCode = Integer.parseInt(statusLine.substring(codePos + 1, phrasePos));

            return responseCode;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * @deprecated this method always returns {@link InputStream}, of the connection
     */
    @Override
    @Deprecated
    public Object getContent() throws IOException {
        createInputStream();
        return inputStream;
    }

    /**
     * @deprecated this method always returns {@link InputStream}, of the connection or null
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

        return inputStream;
    }

    private static final byte[] z = new byte[0];

    @SuppressWarnings("all")
    private void createInputStream() throws IOException {
        connect();

        if (inputStream == null) {
            inputStream = curl.newInputStream();

            // Most callers of this class expect call to getInputStream to progress download
            // past header parsing phase. Do it for them
            inputStream.read(z);
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
    public void setRequestProperty(@NonNull String key, String value) {
        assertNotConnected();

        curl.setHeaderField(key, value);
    }

    @Override
    public void addRequestProperty(@NonNull String key, String value) {
        assertNotConnected();

        curl.addHeaderField(key, value);
    }

    @Override
    public String getRequestProperty(@NonNull String key) {
        assertNotConnected();

        return curl.getRequestProperty(key);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        assertNotConnected();

        final String[] headerArray = curl.getRequestHeaders();

        if (headerArray == null) {
            return Collections.emptyMap();
        }

        final HashMap<String, List<String>> map = new HashMap<>(headerArray.length);

        for (int i = 0; i < headerArray.length && headerArray[i] != null; i += 2) {
            final String key = headerArray[i];
            final String value = headerArray[i + 1];

            final List<String> existing = map.get(key);

            ArrayList<String> arrayList = null;

            if (existing == null) {
                map.put(key, Collections.singletonList(value));
            } else {
                if (existing.size() == 1) {
                    arrayList = new ArrayList<>(2);
                    arrayList.add(existing.get(0));
                    map.put(key, arrayList);
                } else {
                    arrayList = (ArrayList<String>) existing;
                }
            }

            if (arrayList != null) {
                arrayList.add(value);
            }
        }

        return map;
    }

    @Override
    public boolean usingProxy() {
        return getProxyType() != CurlProxy.NONE;
    }

    private String getProxyAddress() {
        return proxy == null || !usingProxy() ? null : proxy.address().toString();
    }

    public interface Config {
        String getDnsServers();

        String getNetworkInterface();

        Proxy getProxy(String url);
    }
}
