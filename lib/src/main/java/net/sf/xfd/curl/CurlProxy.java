package net.sf.xfd.curl;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;

public final class CurlProxy extends Proxy {
    public static final int NONE = 0;
    public static final int HTTP_PLAIN = 1;
    public static final int HTTP_CONNECT = 2;
    public static final int HTTPS_PLAIN = 3;
    public static final int HTTPS_CONNECT = 4;
    public static final int SOCKS4 = 5;
    public static final int SOCKS4a = 6;
    public static final int SOCKS5 = 7;
    public static final int SOCKS5h = 8;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NONE, HTTP_PLAIN, HTTP_CONNECT, HTTPS_PLAIN, HTTPS_CONNECT, SOCKS4, SOCKS4a, SOCKS5, SOCKS5h})
    public @interface ProxyType {}

    private final int proxyType;
    private final InetSocketAddress proxyAddress;

    private CurlProxy(Builder builder) {
        super(Type.DIRECT, null);

        this.proxyType = builder.type;
        this.proxyAddress = builder.address;
    }

    @Override
    public SocketAddress address() {
        return proxyAddress;
    }

    @Override
    public Type type() {
        switch (proxyType) {
            case HTTP_PLAIN:
            case HTTP_CONNECT:
            case HTTPS_PLAIN:
            case HTTPS_CONNECT:
                return Type.HTTP;
            case SOCKS4:
            case SOCKS4a:
            case SOCKS5:
            case SOCKS5h:
                return Type.SOCKS;
            default:
                return Type.DIRECT;
        }
    }

    @ProxyType
    public static int getType(@NonNull Proxy proxy) {
        if (proxy.getClass() == CurlProxy.class) {
            return ((CurlProxy) proxy).proxyType;
        }

        switch (proxy.type()) {
            case HTTP:
                return HTTP_CONNECT;
            case SOCKS:
                return SOCKS5;
            case DIRECT:
            default:
                return NONE;
        }
    }

    public static final class Builder {
        private int type;
        private InetSocketAddress address;

        public Builder setAddress(String proxyHost, int proxyPort) {
            this.address = InetSocketAddress.createUnresolved(proxyHost, proxyPort);

            return this;
        }

        public Builder setType(int type) {
            this.type = type;

            return this;
        }

        public Proxy build() {
            return new CurlProxy(this);
        }
    }
}
