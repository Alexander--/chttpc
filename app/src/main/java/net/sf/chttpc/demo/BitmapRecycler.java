package net.sf.chttpc.demo;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.qozix.tileview.graphics.TileRecycler;
import com.qozix.tileview.tiles.Tile;

import java.util.concurrent.ArrayBlockingQueue;

public final class BitmapRecycler implements TileRecycler {
    private static final int CACHE_BOUND = 20;

    private final Bitmap empty = Bitmap.createBitmap(256, 256, Bitmap.Config.ALPHA_8); {
        empty.eraseColor(Color.TRANSPARENT);
    }

    private final ArrayBlockingQueue<Bitmap> cache = new ArrayBlockingQueue<>(CACHE_BOUND);

    private final WorkSet inUse = new WorkSet(this);

    private final BadCache badCache = new BadCache(inUse);

    public BitmapRecycler() {
    }

    public BadCache getCache() {
        return badCache;
    }

    public WorkSet getBusySet() {
        return inUse;
    }

    public Bitmap placeholder() {
        return empty;
    }

    public Bitmap obtain() {
        return cache.poll();
    }

    @Override
    public void recycleTile(Tile tile) {
        //ObjectHashSet<?> t;
        //t.
        final Bitmap bitmap = tile.getBitmap();

        if (bitmap == null || bitmap == empty) {
            return;
        }

        inUse.remove(bitmap);
    }

    @Override
    public void recycleBitmap(Bitmap bitmap) {
        if (!bitmap.isRecycled() && !cache.offer(bitmap)) {
            bitmap.recycle();
        }
    }
}
