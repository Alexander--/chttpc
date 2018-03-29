package net.sf.chttpc;

import android.Manifest.permission;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;

import net.sf.xfd.UsedByJni;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.net.BindException;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import javax.net.ssl.SSLException;

public final class Curl {
    public static final int EVENT_HEADERS = 0;
    public static final int EVENT_INPUT = 1;
    public static final int EVENT_OUTPUT = 2;

    private static volatile boolean initialized;

    /**
     * Initialize and set up chttpc to use in place of system builtin {@link HttpURLConnection}
     * implementation.
     *
     * This method must be called exactly once, as early during application lifetime as possible,
     * perferably during {@link Application#attachBaseContext}. Other places are also ok, as
     * long as it is called once performing HTTP requests.
     *
     * @param context reference to application context (other kinds of {@link Context} are also ok)
     */
    public static void init(@NonNull Context context) {
        synchronized (Build.class) {
            if (!initialized) {
                installFactory(context);

                initialized = true;
            }
        }
    }

    private static void installFactory(Context context) {
        final URLStreamHandlerFactory factory = new ConnectionBuilder(context).build();

        URL.setURLStreamHandlerFactory(factory);
    }

    final long curlPtr;
    final ByteBuffer buffer;

    @UsedByJni
    private Curl(long ptr, ByteBuffer state) {
        this.curlPtr = ptr;
        this.buffer = state;
    }

    public interface Listener {
        void onEvent(int event);
    }

    public interface CurlFactory {
        @NonNull
        CurlHttp getCurl();
    }

    public interface DnsSource {
        @Nullable
        String getDnsServer();
    }

    public interface ProxySource {
        @Nullable
        Proxy getProxy(MutableUrl url);
    }

    public interface InterfaceSource {
        @Nullable
        String getNetworkInterface(MutableUrl url);
    }

    @SuppressWarnings("MissingPermission")
    public static final class ConnectionBuilder {
        private final Context context;

        private CurlFactory curlFactory;
        private DnsSource dnsSource;
        private InterfaceSource ifSource;
        private ReferenceQueue<Object> refQueue;
        private ProxySource proxySource;

        public ConnectionBuilder(Context context) {
            this.context = context.getApplicationContext();
        }

        public ConnectionBuilder setCurlFactory(@NonNull CurlFactory curlFactory) {
            this.curlFactory = curlFactory;

            return this;
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

            if (refQueue == null) {
                final ConnectionReaper reaper = new ConnectionReaper();

                refQueue = reaper.queue;

                reaper.start();
            }

            if (curlFactory == null) {
                curlFactory = new CurlMaker(refQueue);
            }

            return new CurlURLStreamHandlerFactory(curlFactory, ifSource, dnsSource, proxySource);
        }
    }

    private static final class CurlMaker implements CurlFactory {
        private final ReferenceQueue<Object> queue;

        CurlMaker(ReferenceQueue<Object> queue) {
            this.queue = queue;
        }

        @NonNull
        @Override
        public CurlHttp getCurl() {
            return CurlHttp.create(queue);
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

    private static final class ReusableConnection extends CurlConnection {
        private final CurlURLStreamHandlerFactory factory;

        ReusableConnection(@NonNull CurlURLStreamHandlerFactory factory) {
            super(factory);

            this.factory = factory;
        }

        @Override
        public void disconnect() {
            super.disconnect();

            CurlHttp recycled;

            // this lock is here to prevent pollution of global connection cache with duplicates
            // that might otherwise happen if someone calls disconnect() from different threads
            synchronized (this) {
                recycled = recycle();
            }

            if (recycled != null) {
                factory.recycle(recycled);
            }
        }
    }

    public static final class CurlURLStreamHandlerFactory implements URLStreamHandlerFactory, CurlConnection.Config {
        private final CurlURLStreamHandler handler;
        private final CurlFactory curlFactory;
        private final DnsSource dnsSource;
        private final InterfaceSource interfaceSource;
        private final ProxySource proxySource;

        private final Queue<CurlHttp> cache = new ArrayBlockingQueue<>(4);

        public CurlURLStreamHandlerFactory(CurlFactory curlFactory,
                                           InterfaceSource interfaceSource,
                                           DnsSource dnsSource,
                                           ProxySource proxySource) {
            this.curlFactory = curlFactory;
            this.dnsSource = dnsSource;
            this.proxySource = proxySource;
            this.interfaceSource = interfaceSource;
            this.handler = new CurlURLStreamHandler(this);
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

        public void recycle(CurlHttp curl) {
            cache.offer(curl);
        }

        public CurlHttp obtain() {
            CurlHttp cached = cache.poll();

            if (cached != null) {
                return cached;
            }

            return curlFactory.getCurl();
        }

        @NonNull
        @Override
        public CurlHttp getCurl() {
            return obtain();
        }

        @Override
        public String getDnsServers() {
            return dnsSource == null ? null : dnsSource.getDnsServer();
        }

        @Override
        public String getNetworkInterface(@NonNull MutableUrl url) {
            return interfaceSource == null ? null : interfaceSource.getNetworkInterface(url);
        }

        @Override
        public Proxy getProxy(@NonNull MutableUrl url) {
            return proxySource == null ? null : proxySource.getProxy(url);
        }
    }

    public static final class CurlURLStreamHandler extends URLStreamHandler {
        private final CurlURLStreamHandlerFactory config;

        private CurlURLStreamHandler(CurlURLStreamHandlerFactory config) {
            this.config = config;
        }

        @Override
        public CurlConnection openConnection(URL url) throws IOException {
            final CurlConnection connection = new ReusableConnection(config);
            final CurlHttp curl = connection.getCurl();
            setUrl(curl, url);
            connection.setProxy(config.getProxy(curl.url));
            connection.setRequestProperty("Expect", null);
            return connection;
        }

        @Override
        public CurlConnection openConnection(URL url, Proxy proxy) throws IOException {
            final CurlConnection connection = new ReusableConnection(config);
            setUrl(connection.getCurl(), url);
            connection.setProxy(proxy);
            connection.setRequestProperty("Expect", null);
            return connection;
        }

        private static void setUrl(CurlHttp curl, URL url) {
            final MutableUrl template = curl.getUrl();

            final String p = url.getProtocol();
            final String a = url.getAuthority();
            final String r = url.getRef();
            final String f = url.getFile();

            int len = 1 + p.length()
                    + (a == null ? 0 : 2 + a.length())
                    + (f == null ? 0 : 2 + f.length())
                    + (r == null ? 0 : 1 + r.length());

            template.setLength(len);
            template.elementsCount = 0;

            template.append(p);
            template.append(":");

            if (a != null) {
                template.append("//");
                template.append(a);
            }

            if (f != null) {
                template.append(f);
            }

            if (r != null) {
                template.append("#");
                template.append(r);
            }
        }
    }

    private static final class LegacyDnsDetector implements DnsSource {
        @Override
        public String getDnsServer() {
            return Curl.getDnsCompat();
        }
    }

    @RequiresApi(api = VERSION_CODES.N)
    private static final class NougatNetworkDetector extends ConnectivityManager.NetworkCallback implements DnsSource {
        private final ConnectivityManager netMgr;

        private volatile String dnsServer;

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
        public void onLost(Network network) {
            dnsServer = null;
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
    private static final int ERROR_SOCKET_MYSTERY = 10;
    private static final int ERROR_OOM = 11;
    private static final int ERROR_PROTOCOL = 12;
    private static final int ERROR_ILLEGAL_STATE = 13;
    private static final int ERROR_INTERRUPTED = 14;
    private static final int ERROR_CLOSED = 15;
    private static final int ERROR_OTHER = 16;

    @UsedByJni
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
            case ERROR_SOCKET_MYSTERY:
                throw new SocketException(message);
            case ERROR_DNS_FAILURE:
                throw new UnknownHostException(message);
            case ERROR_PROTOCOL:
                throw new ProtocolException(message);
            case ERROR_ILLEGAL_STATE:
                throw new IllegalStateException(message);
            case ERROR_OOM:
                throw new OutOfMemoryError();
            case ERROR_CLOSED:
                throw new IOException("Already closed");
            case ERROR_SOCKET_CONNECT_TIMEOUT:
                throw new SocketTimeoutException("Socket connect timeout reached");
            case ERROR_SOCKET_READ_TIMEOUT:
                throw new SocketTimeoutException("Socket read timeout reached");
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
            case ERROR_OTHER:
                throw new IOException(message);
        }
    }

    static native void nativeInit();

    public static native void setListener(long curlPtr, Listener listener);

    static native Curl nativeCreate(long parentPtr, int flags);

    static native void setOptionInt(long curlPtr, long value, int option);

    static native char[] nativeConfigure(
            long curlPtr,
            long i10nPtr,
            long fixedLength,
            char[] urlString,
            String method,
            String proxy,
            String dns,
            String ifName,
            int urlLength,
            int proxyType,
            int requestMethod,
            boolean followRedirects,
            boolean doInput,
            boolean doOutput) throws IOException;

    static native int read(long curlPtr, long i10nPtr, Object buf, int off, int count) throws IOException;

    static native int write(long curlPtr, long i10nPtr, Object buf, int off, int count) throws IOException;

    static native void closeOutput(long curlPtr, long i10nPtr) throws IOException;

    static native void clearHeaders(long curlPtr);

    static native String[] getHeaders(long curlPtr, boolean b);

    static native void setHeader(long curlPtr, String key, String value, int keyLen, int valueLen);

    static native void addHeader(long curlPtr, String key, String value, int keyLen, int valueLen);

    static native String header(long curlPtr, String key, int l);

    static native long intHeader(long curlPtr, long defVal, String key, int l);

    static native String outHeader(long curlPtr, String key, int l);

    static native void getLastFd(long curlPtr, int fd) throws IOException;

    static native void reset(long curlPtr);

    static native void dispose(long curlPtr);

    static native String getDnsCompat();
}
