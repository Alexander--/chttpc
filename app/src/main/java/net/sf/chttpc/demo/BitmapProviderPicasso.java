package net.sf.chttpc.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.qozix.tileview.graphics.BitmapProvider;
import com.qozix.tileview.tiles.Tile;

import net.sf.chttpc.CurlConnection;
import net.sf.chttpc.CurlHttp;
import net.sf.chttpc.MutableUrl;
import net.sf.xfd.Interruption;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;

public final class BitmapProviderPicasso implements BitmapProvider {
    private static final String TAG = "BitmapProvider";

    private static final int TILE_SIZE = 256;

    private static final String URL_BASE = "http://chttpc.sourceforge.net/map/";

    private final ThreadLocal<LocalData> locals = new LocalCache();

    private final WorkSet inUse;
    private final BadCache memoryCache;
    private final BitmapRecycler recycler;

    public BitmapProviderPicasso(BitmapRecycler recycler) {
        this.recycler = recycler;

        this.inUse = recycler.getBusySet();
        this.memoryCache = recycler.getCache();
    }

    public Bitmap getBitmap(Tile tile, Context context ) {
        final LocalData locals = this.locals.get();

        final HttpConnection connection = locals.connection;

        Bitmap bitmap, recycled = null;
        try {
            final TileZLevelManager.ZLevel zLevel = (TileZLevelManager.ZLevel) tile.getDetailLevel();

            final int worldSize = zLevel.worldSizeTiles();

            final int rowNum = worldSize - 1 - tile.getRow();
            final int colNum = tile.getColumn();

            //Log.v(TAG, "" + rowNum + ' ' + colNum + ' ' + zLevel.getZ());

            if (rowNum < 0 || rowNum >= worldSize || colNum < 0 || colNum >= worldSize) {
                return recycler.placeholder();
            }

            final long key = TileUtils.getTileKey(tile);

            final Bitmap cached = memoryCache.getCached(key);

            if (cached != null) {
                inUse.add(cached);

                return cached;
            }

            final MutableUrl url = locals.url;

            url.length = URL_BASE.length();
            url.append(zLevel.getZ());
            url.append('/');
            url.append(colNum);
            url.append('/');
            url.append(rowNum);
            url.append("/t.png");

            final Thread thread = Thread.currentThread();

            connection.connect();

            if (thread.isInterrupted()) {
                return recycler.placeholder();
            }

            int httpResponseCode = connection.getResponseCode();

            if (httpResponseCode >= 400) {
                return recycler.placeholder();
            }

            recycled = recycler.obtain();

            if (recycled == null) {
                recycled = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
            }

            final BitmapFactory.Options opts = locals.opts;

            opts.inBitmap = recycled;

            Log.v(TAG, "Starting decoding  for " + rowNum + ' ' + colNum + ' ' + zLevel.getZ());

            bitmap = BitmapFactory.decodeStream(locals.getStream(), null, opts);

            Log.v(TAG, "Completed " + rowNum + ' ' + colNum + ' ' + zLevel.getZ());

            if (bitmap != null && !thread.isInterrupted()) {
                if (recycled == bitmap) {
                    recycled = null;
                }

                inUse.add(bitmap);

                memoryCache.putIntoCache(key, bitmap);

                return bitmap;
            }
        } catch(Throwable t) {
            // probably couldn't find the file, got cancelled in tough way, maybe OOME
            Log.v(TAG, t.toString());

            t.printStackTrace();
        } finally {
            if (recycled != null) {
                recycler.recycleBitmap(recycled);
            }

            connection.disconnect();
        }

        return recycler.placeholder();
    }

    private static final class HttpCore extends CurlHttp {
        HttpCore(MutableUrl url, @Flags int flags) {
            super(url, flags);
        }

        @Override
        protected int httpRead(Interruption i, byte[] buffer, int off, int count) throws IOException {
            return super.httpRead(i, buffer, off, count);
        }
    }

    private static final class HttpConnection extends CurlConnection {
        HttpConnection(@NonNull Config config) {
            super(config);
        }

        @Override
        public void connect() throws IOException {
            final Interruption interruption = Interruption.begin();
            try {
                if (interruption.isInterrupted()) {
                    return;
                }

                configure(interruption);

                if (!interruption.isInterrupted()) {
                    return;
                }

                connected = true;
            } finally {
                Interruption.end();
            }
        }
    }

    private static final class HttpStream extends InputStream {
        private final HttpCore httpCore;

        HttpStream(HttpCore httpCore) {
            this.httpCore = httpCore;
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
            return doRead(b, off, len);
        }

        private int doRead(byte[] b, int off, int len) throws IOException {
            final Interruption helper = Interruption.begin();
            try {
                if (helper.isInterrupted()) {
                    return -1;
                }

                int read = httpCore.httpRead(helper, b, off, len);

                return helper.isInterrupted() ? -1 : read;
            } finally {
                Interruption.end();
            }
        }
    }

    private static final class LocalCache extends ThreadLocal<LocalData> {
        @Override
        protected LocalData initialValue() {
            return new LocalData();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class LocalData implements CurlConnection.Config {
        private final Interruption i = Interruption.begin();


        private final byte[] decoderArray = new byte[32 * 1024];

        private final BitmapFactory.Options opts = new BitmapFactory.Options(); {
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inTempStorage = decoderArray;
            opts.inMutable = true;
            opts.inScaled = false;
            opts.inSampleSize = 1;
            opts.inDither = false;
        }

        private final MutableUrl url = new MutableUrl() {
            { append(URL_BASE); }
        };

        private final HttpCore curlCore = new HttpCore(url, CurlHttp.DEFAULT_FLAGS | CurlHttp.FLAG_USE_FAST_OPEN);

        private final HttpConnection connection = new HttpConnection(this);

        private final InputStream stream = new HttpStream(curlCore);

        private final ReusableBuffer buffered = new ReusableBuffer(stream);

        private InputStream getStream() {
            buffered.reset();

            return buffered;
        }

        @NonNull
        @Override
        public CurlHttp getCurl() {
            return curlCore;
        }

        @Nullable
        @Override
        public String getDnsServers() {
            return null;
        }

        @Nullable
        @Override
        public String getNetworkInterface(@NonNull MutableUrl url) {
            return null;
        }

        @Nullable
        @Override
        public Proxy getProxy(@NonNull MutableUrl url) {
            return null;
        }
    }

    private static final class ReusableBuffer extends BufferedInputStream {
        ReusableBuffer(@NonNull InputStream in) {
            super(in);
        }

        @Override
        public synchronized void reset() {
            pos = 0;
            count = 0;
        }

        @Override
        public void close() {
            reset();
        }
    }
}