package net.sf.xfd.curl;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.NetworkOnMainThreadException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.net.BindException;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.List;

import javax.net.ssl.SSLException;

public final class Curl {
    private Curl() {}

    public static ConnectionBuilder builder(@NonNull Context context) {
        return new ConnectionBuilder(context);
    }

    public interface ProxySource {
        @Nullable
        Proxy getProxy(String url);
    }

    public interface DnsSource {
        @Nullable
        String getDnsServer();
    }

    public interface InterfaceSource {
        @Nullable
        String getNetworkInterface();
    }

    @SuppressWarnings("MissingPermission")
    public static final class ConnectionBuilder {
        private final Context context;

        private DnsSource dnsSource;
        private InterfaceSource ifSource;
        private ReferenceQueue<Object> refQueue;
        private ProxySource proxySource;

        public ConnectionBuilder(Context context) {
            this.context = context.getApplicationContext();
        }

        public ConnectionBuilder setDnsSource(@NonNull DnsSource dnsSource) {
            this.dnsSource = dnsSource;

            return this;
        }

        public ConnectionBuilder setInterfaceSource(@NonNull InterfaceSource ifSource) {
            this.ifSource = ifSource;

            return this;
        }

        public ConnectionBuilder setProxySource(@NonNull ProxySource proxySource) {
            this.proxySource = proxySource;

            return this;
        }

        public ConnectionBuilder setQueue(@NonNull ReferenceQueue<Object> refQueue) {
            this.refQueue = refQueue;

            return this;
        }

        private static NougatNetworkDetector modernNetworkDetector;

        @SuppressWarnings({"NewApi", "MissingPermission"})
        private NougatNetworkDetector getModernNetworkDetector() {
            if (modernNetworkDetector == null) {
                modernNetworkDetector = new NougatNetworkDetector(context);
            }

            return modernNetworkDetector;
        }

        private static Boolean hasNetworkStatePermission;

        private boolean hasNetStatePermission() {
            if (hasNetworkStatePermission == null) {
                hasNetworkStatePermission = context.checkCallingOrSelfPermission(permission.ACCESS_NETWORK_STATE)
                        == PackageManager.PERMISSION_GRANTED;
            }

            return hasNetworkStatePermission;
        }

        public CurlURLStreamHandlerFactory build() {
            if (dnsSource == null) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.N && hasNetStatePermission()) {
                    dnsSource = getModernNetworkDetector();
                } else if (Build.VERSION.SDK_INT <= VERSION_CODES.N) {
                    dnsSource = new LegacyDnsDetector();
                }
            }

            if (ifSource == null && Build.VERSION.SDK_INT >= VERSION_CODES.N && hasNetStatePermission()) {
                ifSource = getModernNetworkDetector();
            }

            if (refQueue == null) {
                final ConnectionReaper reaper = new ConnectionReaper();

                refQueue = reaper.queue;

                reaper.start();
            }

            return new CurlURLStreamHandlerFactory(refQueue, ifSource, dnsSource, proxySource);
        }
    }

    private static final class ConnectionReaper extends Thread {
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        ConnectionReaper() {
            super("Curl reaper");
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {
                try {
                    CleanerRef ref = (CleanerRef) queue.remove();
                    ref.close();
                    ref.clear();
                } catch (Throwable ignored) {
                    // ignore interrupts
                }
            }
        }
    }

    public static final class CurlURLStreamHandlerFactory implements URLStreamHandlerFactory, CurlConnection.Config {
        private final CurlURLStreamHandler handler;
        private final DnsSource dnsSource;
        private final InterfaceSource interfaceSource;
        private final ProxySource proxySource;

        public CurlURLStreamHandlerFactory(ReferenceQueue<Object> queue,
                                           InterfaceSource interfaceSource,
                                           DnsSource dnsSource,
                                           ProxySource proxySource) {
            this.dnsSource = dnsSource;
            this.proxySource = proxySource;
            this.interfaceSource = interfaceSource;
            this.handler = new CurlURLStreamHandler(queue, this);
        }

        @Override
        public CurlURLStreamHandler createURLStreamHandler(String protocol) {
            switch (protocol.toLowerCase()) {
                case "http":
                case "https":
                    return handler;
                default:
                    throw new UnsupportedOperationException("Unsupported protocol: " + protocol);
            }
        }

        @Override
        public String getDnsServers() {
            return dnsSource == null ? null : dnsSource.getDnsServer();
        }

        @Override
        public String getNetworkInterface() {
            return interfaceSource == null ? null : interfaceSource.getNetworkInterface();
        }

        @Override
        public Proxy getProxy(String url) {
            return proxySource == null ? null : proxySource.getProxy(url);
        }
    }

    @SuppressWarnings("MissingPermission")
    public static final class CurlURLStreamHandler extends URLStreamHandler {
        private final ReferenceQueue<Object> refQueue;
        private final CurlConnection.Config config;

        private CurlURLStreamHandler(ReferenceQueue<Object> refQueue, CurlConnection.Config config) {
            this.refQueue = refQueue;
            this.config = config;
        }

        @Override
        public CurlConnection openConnection(URL url) throws IOException {
            final String urlString = url.toExternalForm();
            return openConnection(url, config.getProxy(urlString));
        }

        @Override
        public CurlConnection openConnection(URL url, Proxy proxy) throws IOException {
            return openConnection(url.toExternalForm(), proxy);
        }

        private CurlConnection openConnection(String url, Proxy proxy) {
            final CurlConnection connection = new CurlConnection(CurlHttp.create(refQueue), config);
            connection.setProxy(proxy);
            connection.setRequestProperty("Expect", null);
            connection.setUrlString(url);
            return connection;
        }
    }

    private static final class LegacyDnsDetector implements DnsSource {
        @Override
        public String getDnsServer() {
            return Curl.getDnsCompat();
        }
    }

    @RequiresApi(api = VERSION_CODES.N)
    private static final class NougatNetworkDetector extends ConnectivityManager.NetworkCallback implements DnsSource, InterfaceSource {
        private final ConnectivityManager netMgr;

        private volatile String dnsServer;
        private volatile String iFace;

        @RequiresPermission(permission.ACCESS_NETWORK_STATE)
        private NougatNetworkDetector(Context context) {
            this.netMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            netMgr.registerDefaultNetworkCallback(this);
        }

        @Override
        public String getDnsServer() {
            return dnsServer;
        }

        @Override
        public String getNetworkInterface() {
            return iFace;
        }

        @Override
        public void onLost(Network network) {
            dnsServer = null;
            iFace = null;
        }

        @Override
        public void onAvailable(Network network) {
            updateLinkCfg(netMgr.getLinkProperties(network));
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            updateLinkCfg(linkProperties);
        }

        private void updateLinkCfg(LinkProperties linkProperties) {
            iFace = linkProperties.getInterfaceName();

            final List<InetAddress> servers = linkProperties.getDnsServers();

            if (!servers.isEmpty()) {
                final String address = servers.get(0).getHostAddress();

                if (!address.isEmpty()) {
                    dnsServer = address;

                    return;
                }
            }

            dnsServer = null;
        }
    }

    private static final int ERROR_USE_SYNCHRONIZATION = 0;
    private static final int ERROR_DNS_FAILURE = 1;
    private static final int ERROR_NOT_EVEN_HTTP = 2;
    private static final int ERROR_SOCKET_CONNECT_REFUSED = 3;
    private static final int ERROR_SOCKET_CONNECT_TIMEOUT = 4;
    private static final int ERROR_SOCKET_READ_TIMEOUT = 5;
    private static final int ERROR_RETRY_IMPOSSIBLE = 6;
    private static final int ERROR_BAD_URL = 7;
    private static final int ERROR_BINDING_FAILURE = 8;
    private static final int ERROR_SSL_FAIL = 9;
    private static final int ERROR_SOCKET_MISTERY = 10;
    private static final int ERROR_OOM = 11;

    @SuppressWarnings("unused")
    private static void throwException(String message, int type, int arg) throws IOException {
        switch (type) {
            case ERROR_BAD_URL:
                throw new MalformedURLException(message);
            case ERROR_BINDING_FAILURE:
                throw new BindException(message);
            case ERROR_NOT_EVEN_HTTP:
                throw new UnknownServiceException(message);
            case ERROR_SOCKET_CONNECT_REFUSED:
                throw new ConnectException(message);
            case ERROR_SSL_FAIL:
                throw new SSLException(message);
            case ERROR_SOCKET_MISTERY:
                throw new SocketException(message);
            case ERROR_DNS_FAILURE:
                throw new UnknownHostException(message);
            case ERROR_OOM:
                throw new OutOfMemoryError();
            case ERROR_SOCKET_CONNECT_TIMEOUT:
            case ERROR_SOCKET_READ_TIMEOUT:
                throw new SocketTimeoutException("Socket timeout reached");
            case ERROR_USE_SYNCHRONIZATION:
                throw new IllegalThreadStateException(
                        "You have called methods of this class on thread " + Thread.currentThread() + " " +
                        "while it is still being used in another thread. This is not supported. Do not " +
                        "share instances of this class between threads without appropriate synchronization");
            case ERROR_RETRY_IMPOSSIBLE:
                throw new HttpRetryException(
                        "Sending data has failed due to server authentication demands ot keep-alive failure. " +
                        "The request can not be retried automatically, because of streaming transfer in progress. " +
                        "Retry manually.",
                        arg);
        }
    }

    static native void nativeInit();

    static native long nativeCreate(boolean enableTcpKeepAlive,
                                    boolean enableFalseStart,
                                    boolean enableFastOpen);

    static native char[] nativeConfigure(
            long curlPtr,
            long fixedLength,
            char[] urlString,
            String method,
            String proxy,
            String dns,
            String ifName,
            int urlLength,
            int readTimeout,
            int connectTimeout,
            int proxyType,
            int requestMethod,
            int chunkSize,
            boolean followRedirects,
            boolean doInput,
            boolean doOutput);

    static native void clearHeaders(long curlPtr);

    static native String[] getHeaders(long curlPtr, boolean b);

    static native void setHeader(long curlPtr, String key, String value, int keyLen, int valueLen);

    static native void addHeader(long curlPtr, String key, String value, int keyLen, int valueLen);

    static native String header(long curlPtr, String key, int l);

    static native long intHeader(long curlPtr, long defVal, String key, int l);

    static native String outHeader(long curlPtr, String key, int l);

    static native void nativeReset(long curlPtr);

    static native String getDnsCompat();

    static native int nativeRead(long curlPtr, byte[] buf, int off, int count);

    static native void nativeWrite(long curlPtr, byte[] buf, int off, int count);

    int STATE_ATTACHED  =         0b000000001;
    int STATE_HANDLE_REDIRECT =   0b000000010;
    int STATE_DO_INPUT =          0b000000100;
    int STATE_RECV_PAUSED =       0b000001000;
    int STATE_DO_OUTPUT =         0b000010000;
    int STATE_SEND_PAUSED =       0b000100000;
    int STATE_NEED_INPUT =        0b001000000;
    int STATE_NEED_OUTPUT =       0b010000000;
    int STATE_DONE_SENDING =      0b100000000;

    int x =                       0b001110011;
    static native void nativeCloseOutput(long curlPtr);

    static native void nativeDispose(long curlPtr);
}
