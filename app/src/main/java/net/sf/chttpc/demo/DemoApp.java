package net.sf.chttpc.demo;

import android.app.Application;

import net.sf.chttpc.Curl;
import net.sf.chttpc.CurlHttp;

public final class DemoApp extends Application {
    static {
        System.setProperty(CurlHttp.DEBUG, "true");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Curl.init(this);
    }
}
