package net.sf.chttpc;

import java.io.Closeable;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

/**
 * Automatic cleanup facility for {@link CurlHttp} instances. This class is similar in purpose
 * to Java ByteBuffer Cleaners, and works in similar fashion. Once the "scope" object is garbage
 * collected, the associated {@link CleanerRef} will be added to supplied {@link ReferenceQueue}.
 *
 * You probably don't need this class, unless you are subclassing {@link CurlConnection} and/or
 * manually creating your {@link CurlHttp} instances.
 */
public final class CleanerRef<T> extends PhantomReference<T> implements Closeable {
    private static CleanerRef first = null;

    private CleanerRef next, prev;

    private final long nativePtr;

    private CleanerRef(T scope, long nativePtr, ReferenceQueue<? super T> q) {
        super(scope, q);

        this.nativePtr = nativePtr;
    }

    public static CleanerRef<?> create(Object scope, long nativePtr, ReferenceQueue<Object> q) {
        final CleanerRef ref = new CleanerRef<>(scope, nativePtr, q);

        add(ref);

        return ref;
    }

    private static synchronized void add(CleanerRef cl) {
        if (first != null) {
            cl.next = first;

            first.prev = cl;
        }

        first = cl;
    }

    protected static synchronized boolean remove(CleanerRef cl) {
        // If already removed, do nothing
        if (cl.next == cl) return false;

        cl.clear();

        // Update list
        if (first == cl) {
            if (cl.next != null)
                first = cl.next;
            else
                first = cl.prev;
        }

        if (cl.next != null) cl.next.prev = cl.prev;

        if (cl.prev != null) cl.prev.next = cl.next;

        // Indicate removal by pointing the cleaner to itself

        cl.next = cl;
        cl.prev = cl;

        return true;
    }

    public void close() {
        if (remove(this)) {
            CurlHttp.nativeDispose(nativePtr);
        }
    }
}
