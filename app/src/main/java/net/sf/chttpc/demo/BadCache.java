package net.sf.chttpc.demo;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;


public final class BadCache {
    private static final int MAX_CACHE_ENTRIES = 200;

    private final Long2ObjectLinkedOpenHashMap<Bitmap> cache = new Long2ObjectLinkedOpenHashMap<>(MAX_CACHE_ENTRIES, 1.0f);

    private final WorkSet inUse;

    public BadCache(WorkSet inUse) {
        this.inUse = inUse;
    }

    public synchronized @Nullable Bitmap getCached(long key) {
        return cache.getAndMoveToFirst(key);
    }

    public synchronized void putIntoCache(long key, Bitmap bitmap) {
        final int size = cache.size();

        if (size >= 5) {
            for (int i = 0; i < size; ++i) {
                final Bitmap victim = cache.getAndMoveToFirst(cache.lastLongKey());

                if (inUse.tryRecycle(victim)) {
                    cache.removeFirst();
                    break;
                }
            }
        }

        cache.putAndMoveToFirst(key, bitmap);
    }
}
