package net.sf.chttpc.demo;

import android.graphics.Bitmap;

import com.carrotsearch.hppc.ObjectIdentityHashSet;

public final class WorkSet {
    private final ObjectIdentityHashSet<Bitmap> inUse = new ObjectIdentityHashSet<>(20);

    private final BitmapRecycler recycler;

    public WorkSet(BitmapRecycler recycler) {
        this.recycler = recycler;
    }

    public synchronized void add(Bitmap bitmap) {
        inUse.add(bitmap);
    }

    public synchronized void remove(Bitmap bitmap) {
        inUse.remove(bitmap);
    }

    public synchronized boolean tryRecycle(Bitmap bitmap) {
        if (inUse.contains(bitmap)) return false;

        recycler.recycleBitmap(bitmap);

        return true;
    }
}
