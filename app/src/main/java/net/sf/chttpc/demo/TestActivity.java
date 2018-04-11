package net.sf.chttpc.demo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;

import com.qozix.tileview.TileView;

import net.sf.chttpc.CurlConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

public final class TestActivity extends Activity {

    public static final double NORTH_WEST_LATITUDE = 90.0;
    public static final double NORTH_WEST_LONGITUDE = -180.0;
    public static final double SOUTH_EAST_LATITUDE = -90.0;
    public static final double SOUTH_EAST_LONGITUDE = 180.0;

    //public static final double NORTH_WEST_LATITUDE = 39.9639998777094;
    //public static final double NORTH_WEST_LONGITUDE = -75.17261900652977;
    //public static final double SOUTH_EAST_LATITUDE = 39.93699709962642;
    //public static final double SOUTH_EAST_LONGITUDE = -75.12462846235614;

    private TileView tileView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.act_main);

        try {

            CurlConnection connection = (CurlConnection) new URL("https://google.com").openConnection();

            connection.setDoInput(true);

            /*
            connection.setListener(new CurlConnection.CurlListener() {
                @Override
                public void onHeadersReady(@NonNull CurlConnection connection) throws IOException {
                    Log.i("!!!", "Headers: " + connection.getResponseCode());
                }

                @Override
                public void onInputStreamReady(@NonNull CurlConnection connection, @NonNull InputStream input) throws IOException {
                    input.close();

                    Log.i("!!!", "ISREADY");
                }

                @Override
                public void onOutputStreamReady(@NonNull CurlConnection connection, @NonNull OutputStream output) throws IOException {
                    output.close();

                    Log.i("!!!", "OSREADY");
                }

                @Override
                public void onCompletion(@NonNull CurlConnection connection) {
                    Log.i("!!!", "DONE!!!!");
                }

                @Override
                public void onError(@NonNull CurlConnection connection, @NonNull Throwable error) {
                    Log.e("!!!", "ERR!!!!", error);
                }
            });
            */

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
            Log.println(Log.ERROR, "!!!!!!", connection.getResponseHeader("Content-Type"));

            final Map<String, List<String>> multimap = connection.getHeaderFields();

            Log.println(Log.ERROR, "!!!!!!", multimap.toString());

            Log.println(Log.ERROR, "!!!!!!", "" + connection.getResponseHeader(0));

            Log.println(Log.ERROR, "!!!!!!", "" + connection.getResponseHeader(100));*/
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
        tileView = (TileView) findViewById(R.id.act_main_tiles);

        tileView.setShouldLoopScale(false);

        final int defaultZLevel = 5;

        final TileZLevelManager levelManager = new TileZLevelManager(defaultZLevel, 1.0f);

        tileView.setDetailLevelManager(levelManager);

        levelManager.addTileLevel(3);
        levelManager.addTileLevel(4);

        final BitmapRecycler recycler = new BitmapRecycler();

        int worldSizePx = (int) levelManager.getMapSize();

        tileView.defineBounds(
                NORTH_WEST_LONGITUDE,
                NORTH_WEST_LATITUDE,
                SOUTH_EAST_LONGITUDE,
                SOUTH_EAST_LATITUDE);

        tileView.setTileRecycler(recycler);
        tileView.setBitmapProvider(new BitmapProviderPicasso(recycler));
        tileView.setSize(worldSizePx, worldSizePx);
        tileView.setMarkerAnchorPoints(-0.5f, -1.0f);
        tileView.setBackgroundColor(0xFFe7e7e7);
        tileView.setShouldRenderWhilePanning(true);

        final ImageView downSample = new ImageView(this);
        downSample.setImageResource(R.drawable.preview);
        tileView.addView(downSample, 0);
        */

        //tileView.scrollToAndCenter(-75.15, 39.94d);
    }

    @Override
    protected void onPause() {
        //tileView.pause();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //tileView.resume();
    }

    @Override
    protected void onDestroy() {
        //tileView.destroy();

        super.onDestroy();
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
