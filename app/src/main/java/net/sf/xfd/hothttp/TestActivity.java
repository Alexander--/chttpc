package net.sf.xfd.hothttp;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import net.sf.xfd.curl.Curl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class TestActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        URL.setURLStreamHandlerFactory(Curl.builder(this).build());

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://microsoft.com").openConnection();

            connection.addRequestProperty("X-Foobar", "TestActivity");
            connection.addRequestProperty("X-Foobar", "TestActivity11");
            connection.addRequestProperty("X-Foobar-2", "TestActivity");
            connection.addRequestProperty("X-Timeout", "11");
            connection.addRequestProperty("X-Foobar", "0");
            connection.addRequestProperty("X-Foobar", "egweherhq345qy34ygq4ghb3q4h14h");
            connection.addRequestProperty("Accept", "text/plain");
            connection.addRequestProperty("X-Foobar", "vvv");

            Log.i("!!!", "Header value is: " + connection.getRequestProperty("X-Foobar-2"));

            Log.i("!!!", "Headers: " + connection.getRequestProperties());

            //connection.setRequestMethod("HEAD");
            connection.setDoInput(false);
            connection.setDoOutput(false);
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            Log.i("!!!", "connect() returned");

            //connection.getInputStream();

            //try (Writer w = new LogWriter("TestActivity")) {
            //    w.write(convertStreamToString(connection.getInputStream()));
            //}

            /*
            Log.println(Log.ERROR, "!!!!!!", connection.getHeaderField("Content-Type"));

            final Map<String, List<String>> multimap = connection.getHeaderFields();

            Log.println(Log.ERROR, "!!!!!!", multimap.toString());

            Log.println(Log.ERROR, "!!!!!!", "" + connection.getHeaderField(0));

            Log.println(Log.ERROR, "!!!!!!", "" + connection.getHeaderField(100));
            /**/
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String convertStreamToString(java.io.InputStream is) {
        try (Scanner scanner = new Scanner(is, "UTF-8")) {
            scanner.useLocale(new Locale("ru", "RU"));

            return scanner.useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            e.printStackTrace();

            return "";
        }
    }
}
